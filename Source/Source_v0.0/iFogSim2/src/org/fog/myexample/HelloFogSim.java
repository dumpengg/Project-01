package org.fog.myexample;
import org.fog.application.Application;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Actuator;
import org.fog.placement.Controller;
import org.fog.placement.ModulePlacementMapping;
import org.fog.placement.ModuleMapping;
import org.fog.utils.FogUtils;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;

public class HelloFogSim {
    public static void main(String[] args) {
        try {
            // 1. Initialize CloudSim
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUsers, calendar, traceFlag);

            // 2. Create a single fog device
            FogDevice fogDevice = createFogDevice("FogDevice-1", 2000, 2000, 10000, 10000,
                                                  100, 0.01, 87.53, 82.44);

            // 3. Create application
            Application application = createApplication();
            int userId = application.getUserId();
            String appId = application.getAppId();

            // 4. Create a sensor
            Sensor sensor = new Sensor("Sensor-1", "TEMP", userId, appId,
                                       new DeterministicDistribution(10));

            // 5. Create an actuator
            Actuator actuator = new Actuator("Actuator-1", 
                    application.getUserId(), 
                    application.getAppId(), 
                    "DISPLAY");

            // 6. Map application modules to fog devices
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            moduleMapping.addModuleToDevice("module1", "FogDevice-1");

            // 7. Create controller
            List<FogDevice> fogDevices = Arrays.asList(fogDevice);
            List<Sensor> sensors = Arrays.asList(sensor);
            List<Actuator> actuators = Arrays.asList(actuator);

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application, new ModulePlacementMapping(fogDevices, application, moduleMapping));

            // 8. Start simulation
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("HelloFogSim2 simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper method to create a fog device
    private static FogDevice createFogDevice(String name, long mips, int ram, long upBw, long downBw,
                                             int level, double ratePerMips, double busyPower, double idlePower) throws Exception {
        return FogUtils.createFogDevice(name, mips, ram, upBw, downBw, level, ratePerMips, busyPower, idlePower);
    }

    // Define a simple application
    private static Application createApplication() {
        Application application = Application.createApplication("HelloApp", 1);

        // Add a processing module
        application.addAppModule("module1", 10);

        // Define data flow: TEMP -> module1 -> DISPLAY
        application.addAppEdge("TEMP", "module1", 1000, 500, "TEMP_DATA", AppEdge.SENSOR, 0);
        application.addAppEdge("module1", "DISPLAY", 1000, 500, "DISPLAY_DATA", AppEdge.ACTUATOR, 0);

        // Define loop for latency measurement
        List<AppLoop> loops = new ArrayList<>();
        loops.add(new AppLoop(Arrays.asList("TEMP", "module1", "DISPLAY")));
        application.setLoops(loops);

        return application;
    }
}
