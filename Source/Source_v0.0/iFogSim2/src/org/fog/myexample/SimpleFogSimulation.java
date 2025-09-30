package org.fog.myexample;

import org.fog.entities.*;
import org.fog.application.*;
import org.fog.placement.*;
import org.fog.utils.*;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;

import java.util.*;

public class SimpleFogSimulation {

    public static void main(String[] args) {
        try {
            // Step 1: Initialize CloudSim
            int numUser = 1; 
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUser, calendar, traceFlag);

            // Step 2: Create Fog Devices
            FogDevice cloud = createFogDevice("cloud", 10000, 10000, 10000, 0, 0, 0, 0);
            FogDevice gateway = createFogDevice("gateway", 1000, 1000, 1000, 1, 1, 1, 100);

            // Connect gateway to cloud
            gateway.setParentId(cloud.getId());

            // Step 3: Create Sensors
            DeterministicDistribution transmitDistribution = new DeterministicDistribution(5);

         // Create sensors
         Sensor sensor1 = new Sensor("s1", "IoT-Data", 1, "HelloWorldApp", transmitDistribution);
         Sensor sensor2 = new Sensor("s2", "IoT-Data", 1, "HelloWorldApp", transmitDistribution);


         Actuator actuator = new Actuator("a1", 1, "HelloWorldApp", "ACTUATOR");

            // Step 4: Create Application
            Application app = Application.createApplication("HelloWorldApp", 1);
            app.addAppModule("Module1", 1000);
            app.addAppModule("Module2", 1000);

            // Define edges
          
    
            app.addAppEdge("IoT-Data", "Module1", 3000, 500, "IoT-Data", AppEdge.SENSOR, AppEdge.MODULE);
            app.addAppEdge("Module1", "Module2", 1000, 1000, "Tuple1", AppEdge.MODULE, AppEdge.MODULE);
            app.addAppEdge("Module2", "ACTUATOR", 1000, 500, "Tuple2", AppEdge.MODULE, AppEdge.ACTUATOR);

            // Step 5: Module Placement
          //  ModulePlacement modulePlacement = new ModulePlacementSimple(app, Arrays.asList(cloud, gateway));

            
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            moduleMapping.addModuleToDevice("Module1", "gateway"); // optional
            moduleMapping.addModuleToDevice("Module2", "cloud");

            // Create placement
            ModulePlacementEdgewards modulePlacement = new ModulePlacementEdgewards(
                Arrays.asList(cloud, gateway),  // list of FogDevices
                Arrays.asList(sensor1, sensor2), // sensors
                Arrays.asList(actuator),         // actuators
                app,                             // application
                moduleMapping                     // module mapping
            );
            // Step 6: Start Simulation
            Controller controller = new Controller("master-controller", Arrays.asList(cloud, gateway), Arrays.asList(sensor1, sensor2), Arrays.asList(actuator));
            controller.submitApplication(app, 0, modulePlacement);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	private static FogDevice createFogDevice(String string, int i, int j, int k, int l, int m, int n, int o) {
		// TODO Auto-generated method stub
		return null;
	}

//    // Helper method to create fog devices
//    private static FogDevice createFogDevice(String name, long mips, long ram, long bw, int level, int upBw, int downBw, int latency) {
//        FogDevice device = null;
//        try {
//            device = new FogDevice(name, mips, ram, bw, level, upBw, downBw, latency);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return device;
//    }
//    PowerModel powerModel = new PowerModelLinear(100, 200); // Example: min/max power
//    FogDevice device = new FogDevice(
//            "device-0", 1000, 4000, 1000, 1000, 1.0, powerModel
//    );
    

}
