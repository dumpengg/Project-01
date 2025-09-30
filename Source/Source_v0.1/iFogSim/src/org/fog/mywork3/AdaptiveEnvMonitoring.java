package org.fog.mywork3;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;

import java.util.*;

/**
 * Adaptive Container Scheduling for IoT-Based Real-Time Environmental Monitoring
 * Implements three strategies: Cloud-only, Fog-only, and Adaptive (heuristic) using iFogSim2-compatible APIs.
 * Drop this under the iFogSim2 repo's src/ and run.
 */
public class AdaptiveEnvMonitoring {
    private static final String APP_ID = "env-monitor";

    // System sizes from paper: 1 cloud DC, 2 fog nodes, 10 sensors, 1 actuator
    private static final int NUM_SENSORS = 10;

    // Weights and thresholds for adaptive policy
    public static double ALPHA = 1.0; // latency weight
    public static double BETA  = 1.0; // energy weight
    public static double GAMMA = 1.0; // cloud cost weight
    public static double TAU_L = 300; // ms threshold (example)
    public static double TAU_E = 2.0e5; // energy (J) threshold (example)

    public static void main(String[] args) throws Exception {
        String policy = args.length > 0 ? args[0] : "adaptive"; // cloud|fog|adaptive
        boolean traceFlag = false;
        LogPrinter.banner("Starting policy = "+policy);

        CloudSim.init(1, Calendar.getInstance(), traceFlag);
        FogBroker broker = new FogBroker("broker");

        // Build devices: cloud + 2 fog gateways
        List<FogDevice> fogDevices = new ArrayList<>();
        FogDevice cloud = Utils.createFogDevice("cloud-dc", 40000 /*MIPS*/, 32768, 10000, 10000, 0.01, 16.0, 13.0);
        cloud.setLevel(0);
        fogDevices.add(cloud);

        FogDevice fog1 = Utils.createFogDevice("fog-1", 10000, 8192, 10000, 10000, 0.0, 107.339, 83.4333);
        fog1.setParentId(cloud.getId());
        fog1.setUplinkLatency(20.0); // ms
        fog1.setLevel(1);
        fogDevices.add(fog1);

        FogDevice fog2 = Utils.createFogDevice("fog-2", 10000, 8192, 10000, 10000, 0.0, 107.339, 83.4333);
        fog2.setParentId(cloud.getId());
        fog2.setUplinkLatency(20.0);
        fog2.setLevel(1);
        fogDevices.add(fog2);

        // Build sensors and one actuator
        List<Sensor> sensors = new ArrayList<>();
        List<Actuator> actuators = new ArrayList<>();
        String[] types = new String[]{"AQI","TEMP","HUM"};
        for (int i = 0; i < NUM_SENSORS; i++) {
            String type = types[i % types.length];
            Sensor s = new Sensor("s-"+i, "SENSOR_"+type, broker.getId(), APP_ID, new org.fog.utils.distribution.DeterministicDistribution(5));
            FogDevice gw = (i < NUM_SENSORS/2) ? fog1 : fog2;
            s.setGatewayDeviceId(gw.getId());
            s.setLatency(2.0);
            sensors.add(s);
        }
        Actuator alarm = new Actuator("actuators/alert", broker.getId(), APP_ID, "ALERT_DISPLAY");
        alarm.setGatewayDeviceId(fog1.getId());
        alarm.setLatency(1.0);
        actuators.add(alarm);

        Application application = createApplication(broker.getId());

        // mapping for static cases
        ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
        if ("cloud".equalsIgnoreCase(policy)) {
            moduleMapping.addModuleToDevice("env-processor", cloud.getName());
            moduleMapping.addModuleToDevice("env-storage", cloud.getName());
        } else if ("fog".equalsIgnoreCase(policy)) {
            moduleMapping.addModuleToDevice("env-processor", fog1.getName());
            moduleMapping.addModuleToDevice("env-processor", fog2.getName()); // replicate
            moduleMapping.addModuleToDevice("env-storage", fog1.getName());
        } else if ("adaptive".equalsIgnoreCase(policy)) {
            // Default: map processor and storage to fog1 and fog2 for initial placement
            moduleMapping.addModuleToDevice("env-processor", fog1.getName());
            moduleMapping.addModuleToDevice("env-processor", fog2.getName());
            moduleMapping.addModuleToDevice("env-storage", fog1.getName());
        }

        Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

        if ("adaptive".equalsIgnoreCase(policy)) {
            ModulePlacement placement = new AdaptivePlacement(fogDevices, sensors, actuators, application,
                    ALPHA, BETA, GAMMA, TAU_L, TAU_E);
            // Set initial mapping for adaptive placement
            // placement.setModuleMapping(moduleMapping); // Removed: not needed for AdaptivePlacement
            controller.submitApplication(application, placement);
            LatencyEnergyMonitor monitor = new LatencyEnergyMonitor("monitor", controller, application, (AdaptivePlacement) placement,
                    50 /*ms between checks*/);
        } else if ("fog".equalsIgnoreCase(policy)) {
            controller.submitApplication(application, new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));
        } else { // cloud
            controller.submitApplication(application, new org.fog.placement.ModulePlacementMapping(fogDevices, application, moduleMapping));
        }

        TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
        CloudSim.startSimulation();
        // Ensure simulation runs for at least 1000 ms
        try { Thread.sleep(1000); } catch (InterruptedException e) { }
        CloudSim.stopSimulation();

        LogPrinter.banner("Finished policy = "+policy);
    }

    private static Application createApplication(int userId) {
        Application application = Application.createApplication(APP_ID, userId);

        // Two processing modules (processor + storage)
        application.addAppModule("env-processor", 1000); // MIPS requirement per instance
        application.addAppModule("env-storage", 500);

        // Sensor -> Processor
        application.addAppEdge("SENSOR_AQI", "env-processor", 3000, 500, "AQI_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("SENSOR_TEMP", "env-processor", 3000, 500, "TEMP_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("SENSOR_HUM", "env-processor", 3000, 500, "HUM_DATA", Tuple.UP, AppEdge.SENSOR);

        // Processor -> Storage (for periodic updates)
        application.addAppEdge("env-processor", "env-storage", 2000, 500, "ANALYTICS", Tuple.UP, AppEdge.MODULE);

        // Processor -> Actuator
        application.addAppEdge("env-processor", "ALERT_DISPLAY", 100, 100, "ALERT", Tuple.DOWN, AppEdge.ACTUATOR);

        // Tuple mappings
        application.addTupleMapping("env-processor", "AQI_DATA", "ANALYTICS", new FractionalSelectivity(1.0));
        application.addTupleMapping("env-processor", "TEMP_DATA", "ANALYTICS", new FractionalSelectivity(1.0));
        application.addTupleMapping("env-processor", "HUM_DATA", "ANALYTICS", new FractionalSelectivity(1.0));
        application.addTupleMapping("env-processor", "AQI_DATA", "ALERT", new FractionalSelectivity(0.1));
        application.addTupleMapping("env-processor", "TEMP_DATA", "ALERT", new FractionalSelectivity(0.05));
        application.addTupleMapping("env-processor", "HUM_DATA", "ALERT", new FractionalSelectivity(0.05));

        // Define application loop for latency measurement: sensor -> processor -> actuator
        List<String> loop = new ArrayList<>();
        loop.add("SENSOR_AQI"); loop.add("env-processor"); loop.add("ALERT_DISPLAY");
        List<String> loop2 = new ArrayList<>();
        loop2.add("SENSOR_TEMP"); loop2.add("env-processor"); loop2.add("ALERT_DISPLAY");
        List<String> loop3 = new ArrayList<>();
        loop3.add("SENSOR_HUM"); loop3.add("env-processor"); loop3.add("ALERT_DISPLAY");
        List<AppLoop> loops = new ArrayList<>();
        loops.add(new AppLoop(loop));
        loops.add(new AppLoop(loop2));
        loops.add(new AppLoop(loop3));
        application.setLoops(loops);

        return application;
    }
}