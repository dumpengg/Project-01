package org.fog.work1;

import java.util.*;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;

import org.fog.entities.*;
import org.fog.placement.MicroservicesController;  // iFogSim2 controller

import org.fog.utils.FogUtils;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Storage; // Fix Storage import

public class AdaptiveIFogSim2MicroMain {

    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);
            FogBroker broker = new FogBroker("broker");

            // Topology: cloud -> fog1 -> fog2
            FogDevice cloud = createFogDevice("cloud", 100000, 262144, 10000, 10000, 0, 0.01, 107.339, 83.433);
            FogDevice fog1  = createFogDevice("fog1",   20000,  16384, 10000, 10000, 1, 0.0,   107.339, 83.433);
            FogDevice fog2  = createFogDevice("fog2",   10000,   8192, 10000, 10000, 2, 0.0,   107.339, 83.433);

            fog1.setParentId(cloud.getId()); fog1.setUplinkLatency(10.0);
            fog2.setParentId(fog1.getId());  fog2.setUplinkLatency(4.0);

            List<FogDevice> fogDevices = Arrays.asList(cloud, fog1, fog2);

            // Application: SENSOR -> processing -> ACTUATOR
            String appId = "AdaptiveApp";
            Application app = Application.createApplication(appId, FogUtils.USER_ID);

            // Prevent vm==null NPE (as in official examples)  [1](https://github.com/Cloudslab/iFogSim/blob/main/src/org/fog/utils/FogUtils.java)
            app.setUserId(broker.getId());

            app.addAppModule("processing", 5000);
            app.addAppEdge("SENSOR", "processing", 1000, 1000, "SENSOR",   Tuple.UP,   AppEdge.SENSOR);
            app.addAppEdge("processing", "ACTUATOR", 500, 500, "ACTUATOR", Tuple.DOWN, AppEdge.ACTUATOR);
            app.addTupleMapping("processing", "SENSOR", "ACTUATOR", new FractionalSelectivity(1.0));

            // IO endpoints at fog2
            List<Sensor> sensors = new ArrayList<>();
            List<Actuator> actuators = new ArrayList<>();
            Sensor sensor = new Sensor("s-1", "SENSOR", broker.getId(), appId, new DeterministicDistribution(5.0));
            sensor.setGatewayDeviceId(fog2.getId()); sensor.setLatency(1.0); sensors.add(sensor);
            Actuator actuator = new Actuator("a-1", broker.getId(), appId, "ACTUATOR");
            actuator.setGatewayDeviceId(fog2.getId()); actuator.setLatency(1.0); actuators.add(actuator);

            List<Application> applications = new ArrayList<>();
            applications.add(app);
            List<Integer> clusterLevels = Arrays.asList(1, 2); // Example cluster levels
            Double clusterLatency = 10.0; // Example value
            int placementLogic = 0; // Use 0 or the correct constant/index for your AdaptiveMicroservicePlacement

            MicroservicesController ms = new MicroservicesController(
                "ms-controller",
                fogDevices,
                sensors,
                applications,
                clusterLevels,
                clusterLatency,
                placementLogic
            );

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("Microservices simulation finished.");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // Local copy of device builder (same as in classic runner)
    private static FogDevice createFogDevice(
            String name, long mips, int ramMb, long upBw, long downBw, int level,
            double ratePerMips, double busyPower, double idlePower) throws Exception {

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking(mips)));

        int hostId = FogUtils.generateEntityId();
        long storageMB = 1_000_000;
        int hostBw = 10_000;

        PowerHost host = new PowerHost(
            hostId,
            new RamProvisionerSimple(ramMb),
            new org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking(hostBw),
            storageMB,
            peList,
            new org.fog.scheduler.StreamOperatorScheduler(peList),
            new org.fog.utils.FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        FogDeviceCharacteristics ch = new FogDeviceCharacteristics(
            "x86", "Linux", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0);

        List<Storage> storageList = new LinkedList<>(); // Fix Storage type

        FogDevice device = new FogDevice(
            name, ch, new org.fog.policy.AppModuleAllocationPolicy(hostList),
            storageList, 10, upBw, downBw, 0, ratePerMips);
        device.setLevel(level);
        return device;
    }
}