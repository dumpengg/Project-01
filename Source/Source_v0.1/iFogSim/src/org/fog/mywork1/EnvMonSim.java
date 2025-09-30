package org.fog.mywork1;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.*;
import org.fog.application.Application;
import org.fog.application.AppEdge;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.*;

public class EnvMonSim {

    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();

    public static void main(String[] args) {
        Log.printLine("Starting EnvMonSim...");

        try {
            // Initialize CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            FogBroker broker = new FogBroker("broker");

            // Create Cloud and Fog nodes
            FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01);
            cloud.setParentId(-1);

            FogDevice fogNode = createFogDevice("fog-1", 2800, 4000, 1000, 10000, 1, 0.0);
            fogNode.setParentId(cloud.getId());

            fogDevices.add(cloud);
            fogDevices.add(fogNode);

            // Create application
            Application application = createApplication(broker.getId());

            // Mapping application module to fog node
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            moduleMapping.addModuleToDevice("data_processor", fogNode.getName());

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            // Submit application with placement policy
            controller.submitApplication(application,
                    new ModulePlacementMapping(fogDevices, application, moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("EnvMonSim finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unexpected error");
        }
    }

    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw,
                                             long downBw, int level, double ratePerMips) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // 1 GB
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerTimeShared(peList),
                new PowerModelLinear(87.53, 82.44)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        //Host myhost = hostList.get(0); // pick the first host
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
            "x86", "Linux", "Xen",
            host,
            10.0, 3.0, 0.05, 0.1, 0.1
        );




        FogDevice fogDevice = null;
        try {
            fogDevice = new FogDevice(nodeName, characteristics,
                    new VmAllocationPolicySimple(hostList), new LinkedList<Storage>(),
                    10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }
        fogDevice.setLevel(level);
        return fogDevice;
    }

    private static Application createApplication(int userId) {
        Application application = Application.createApplication("EnvMonApp", userId);

        // Add processing module
        application.addAppModule("data_processor", 10);

        // Define edges: Sensor -> Module -> Actuator
        application.addAppEdge("AQI", "data_processor", 1000, 2000, "AQI", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("data_processor", "Display", 1000, 2000, "Display", Tuple.DOWN, AppEdge.ACTUATOR);

        // Define mapping
        application.addTupleMapping("data_processor", "AQI", "Display", new FractionalSelectivity(1.0));

        // Create a sensor and actuator
        Sensor sensor = new Sensor("s-AQI", "AQI", userId, application.getAppId(),
                new DeterministicDistribution(5));
        sensors.add(sensor);

        Actuator actuator = new Actuator("a-Display", userId, application.getAppId(), "Display");
        actuators.add(actuator);

        // Attach to fog node
        sensor.setGatewayDeviceId(fogDevices.get(1).getId());
        sensor.setLatency(2.0);

        actuator.setGatewayDeviceId(fogDevices.get(1).getId());
        actuator.setLatency(2.0);

        return application;
    }
}
