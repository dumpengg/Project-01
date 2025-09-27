package org.fog.work1;

import java.util.*;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;

import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;

import org.fog.entities.*;

import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;

import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;

import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.distribution.DeterministicDistribution;

public class AdaptiveIFogSim2Main {

    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);
            FogBroker broker = new FogBroker("broker");

            // Build devices (cloud -> fog1 -> fog2)
            FogDevice cloud = createFogDevice("cloud", 100000, 262144, 10000, 10000, 0, 0.01, 107.339, 83.433);
            FogDevice fog1  = createFogDevice("fog1",   20000,  16384, 10000, 10000, 1, 0.0,   107.339, 83.433);
            FogDevice fog2  = createFogDevice("fog2",   10000,   8192, 10000, 10000, 2, 0.0,   107.339, 83.433);

            fog1.setParentId(cloud.getId()); fog1.setUplinkLatency(10.0);
            fog2.setParentId(fog1.getId());  fog2.setUplinkLatency(4.0);

            List<FogDevice> fogDevices = Arrays.asList(cloud, fog1, fog2);

            // Application: SENSOR -> processing -> ACTUATOR
            String appId = "AdaptiveApp";
            Application app = Application.createApplication(appId, FogUtils.USER_ID);

            // CRITICAL: prevents vm==null NPE (tuples carry broker's userId)  [1](https://github.com/Cloudslab/iFogSim/blob/main/src/org/fog/utils/FogUtils.java)
            app.setUserId(broker.getId());

            app.addAppModule("processing", 5000);
            app.addAppEdge("SENSOR", "processing", 1000, 1000, "SENSOR", Tuple.UP,   AppEdge.SENSOR);
            app.addAppEdge("processing", "ACTUATOR", 500, 500, "ACTUATOR", Tuple.DOWN, AppEdge.ACTUATOR);
            app.addTupleMapping("processing", "SENSOR", "ACTUATOR", new FractionalSelectivity(1.0));

            // One sensor & one actuator at fog2 (constructors as in examples)  [1](https://github.com/Cloudslab/iFogSim/blob/main/src/org/fog/utils/FogUtils.java)
            List<Sensor> sensors = new ArrayList<>();
            List<Actuator> actuators = new ArrayList<>();

            Sensor sensor = new Sensor("s-1", "SENSOR", broker.getId(), appId, new DeterministicDistribution(5.0));
            sensor.setGatewayDeviceId(fog2.getId());
            sensor.setLatency(1.0);
            sensors.add(sensor);

            Actuator actuator = new Actuator("a-1", broker.getId(), appId, "ACTUATOR");
            actuator.setGatewayDeviceId(fog2.getId());
            actuator.setLatency(1.0);
            actuators.add(actuator);

            // Map "processing" to fog2
            ModuleMapping mm = ModuleMapping.createModuleMapping();
            mm.addModuleToDevice("processing", "fog2");

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(app, 0, new ModulePlacementMapping(fogDevices, app, mm));

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("Simulation finished.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create FogDevice in the canonical iFogSim2 way:
     * Host -> FogDeviceCharacteristics -> FogDevice.  [2](https://www.youtube.com/watch?v=TPZUexK3fyo)[3](https://deepai.org/publication/ifogsim2-an-extended-ifogsim-simulator-for-mobility-clustering-and-microservice-management-in-edge-and-fog-computing-environments)
     */
    private static FogDevice createFogDevice(
            String name, long mips, int ramMb, long upBw, long downBw, int level,
            double ratePerMips, double busyPower, double idlePower) throws Exception {

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        int hostId = FogUtils.generateEntityId();
        long storageMB = 1_000_000;  // MB
        int hostBw    = 10_000;      // Mb/s

        PowerHost host = new PowerHost(
            hostId,
            new RamProvisionerSimple(ramMb),
            new BwProvisionerOverbooking(hostBw),
            storageMB,
            peList,
            new StreamOperatorScheduler(peList),
            new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        FogDeviceCharacteristics ch = new FogDeviceCharacteristics(
            "x86", "Linux", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0);

        List<Storage> storageList = new LinkedList<>();

        FogDevice device = new FogDevice(
            name, ch, new AppModuleAllocationPolicy(hostList),
            storageList, /*schedulingInterval*/10,
            upBw, downBw, /*uplinkLatency*/0, ratePerMips);

        device.setLevel(level);
        return device;
    }
}
