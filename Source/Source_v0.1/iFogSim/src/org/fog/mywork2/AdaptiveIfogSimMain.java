package org.fog.mywork2;

import java.util.*;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.placement.Controller;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Actuator;
import org.fog.utils.FogUtils;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;

public class AdaptiveIfogSimMain {

    public static void main(String[] args) {
        try {
            int numUser = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUser, calendar, traceFlag);

            FogDevice cloud = createCloud("cloud");
            FogDevice fog1 = createFogDevice("fog1");
            FogDevice fog2 = createFogDevice("fog2");
            List<FogDevice> fogDevices = Arrays.asList(cloud, fog1, fog2);

            int userId = 1;
            Application app = Application.createApplication("AdaptiveApp", userId);
            app.addAppModule("sensor", 10);
            app.addAppModule("processing", 5000);
            app.addAppModule("actuator", 20);

            List<Sensor> sensors = new ArrayList<>();
            List<Actuator> actuators = new ArrayList<>();
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            // Adaptive placement logic: print placement for each module
            AdaptiveMicroservicePlacement placementLogic = new AdaptiveMicroservicePlacement();
            System.out.println("\nAdaptive Placement Decisions:");
            for (org.fog.application.AppModule module : app.getModules()) {
                Map<String, Integer> placement = placementLogic.makePlacementDecision(fogDevices, module);
                for (Map.Entry<String, Integer> entry : placement.entrySet()) {
                    System.out.println("Module: " + entry.getKey() + ", Device ID: " + entry.getValue());
                }
            }

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Simulation finished.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static FogDevice createCloud(String name) throws Exception {
        // Example values for a cloud device
        return createBasicFogDevice(name, 100000, 262144, 1000, 0, 0.01);
    }

    private static FogDevice createFogDevice(String name) throws Exception {
        // Example values for a fog device
        return createBasicFogDevice(name, 10000, 8192, 100, 20, 0.0);
    }

    private static FogDevice createBasicFogDevice(String name, int mips, int ram, int upBw, int downBw, double ratePerMips) throws Exception {
        int hostId = FogUtils.generateEntityId();
        int storage = 1000000; // just an example
        List<Storage> storageList = new ArrayList<>();
        List<org.cloudbus.cloudsim.Pe> peList = new ArrayList<>();
        peList.add(new org.cloudbus.cloudsim.Pe(0, new PeProvisionerSimple(mips)));
        PowerHost host = new PowerHost(
            hostId,
            new RamProvisionerSimple(ram),
            new BwProvisionerSimple(upBw),
            storage,
            peList,
            new VmSchedulerTimeShared(peList),
            new PowerModelLinear(100, 0.01)
        );
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
            "x86", // architecture
            "Linux", // os
            "Xen", // vmm
            host,
            10.0, // timeZone
            3.0, // costPerSec
            0.05, // costPerMem
            0.001, // costPerStorage
            0.0 // costPerBw
        );
        List<Host> hostList = new ArrayList<>();
        hostList.add(host);
        return new FogDevice(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
    }
}