package org.fog.mywork2;

import java.util.*;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;                 // adjust import if needed
import org.fog.entities.FogDevice;                  // adjust import if needed

public class AdaptiveMicroservicePlacement {

    private double alpha = 0.6;
    private double beta = 0.3;
    private double gamma = 0.1;
    private double hysteresis = 1.0;

    public AdaptiveMicroservicePlacement() {
        // No superclass constructor
    }

    public void setWeights(double a, double b, double g) {
        this.alpha = a; this.beta = b; this.gamma = g;
    }

    public Map<String, Integer> makePlacementDecision(List<FogDevice> candidateDevices, AppModule module) {
        Map<String, Integer> placementMap = new HashMap<>();
        String moduleName = module.getName();

        double bestJ = Double.POSITIVE_INFINITY;
        FogDevice bestDevice = null;

        for (FogDevice device : candidateDevices) {
            double estLatency = estimateLatency(device, module);
            double estEnergy = estimateEnergy(device, module);
            double estCost = estimateCost(device, module);

            double J = alpha * estLatency + beta * estEnergy + gamma * estCost;
            if (J + hysteresis < bestJ) {
                bestJ = J;
                bestDevice = device;
            }
        }

        if (bestDevice != null) {
            placementMap.put(moduleName, bestDevice.getId());
        }
        return placementMap;
    }

    private double estimateLatency(FogDevice device, AppModule module) {
        double netMs = device.getUplinkLatency();
        double procMs = estimateProcessingMs(device, module);
        return netMs + procMs;
    }

    private double estimateProcessingMs(FogDevice device, AppModule module) {
        if (module == null) return 1000.0;
        double mi = getMiPerTuple(module);
        double mips = getMips(device);
        double sec = mi / Math.max(1.0, mips);
        return sec * 1000.0;
    }

    private double estimateEnergy(FogDevice device, AppModule module) {
        double powerW = device.getPower();
        double procSec = estimateProcessingMs(device, module) / 1000.0;
        return powerW * procSec;
    }

    private double estimateCost(FogDevice device, AppModule module) {
        if (isCloudDevice(device)) {
            double mi = getMiPerTuple(module);
            return (mi / 1e6) * 0.01;
        } else {
            return 0.0;
        }
    }

    private boolean isCloudDevice(FogDevice d) {
        return d.getName().toLowerCase().contains("cloud");
    }

    // Helper methods to access MI and MIPS, since original methods do not exist
    private double getMiPerTuple(AppModule module) {
        // Replace with actual logic if available
        try {
            return (double) module.getClass().getMethod("getMiPerTuple").invoke(module);
        } catch (Exception e) {
            return 1000.0;
        }
    }

    private double getMips(FogDevice device) {
        // Replace with actual logic if available
        try {
            return (double) device.getClass().getMethod("getMips").invoke(device);
        } catch (Exception e) {
            return 1000.0;
        }
    }

    public static void main(String[] args) {
        AdaptiveMicroservicePlacement placement = new AdaptiveMicroservicePlacement();
        try {
            // Minimal Pe and Host for FogDeviceCharacteristics
            List<org.cloudbus.cloudsim.Pe> peList = new ArrayList<>();
            peList.add(new org.cloudbus.cloudsim.Pe(0, new org.cloudbus.cloudsim.provisioners.PeProvisionerSimple(1000)));
            org.cloudbus.cloudsim.Host host = new org.cloudbus.cloudsim.Host(
                0, new org.cloudbus.cloudsim.provisioners.RamProvisionerSimple(2048),
                new org.cloudbus.cloudsim.provisioners.BwProvisionerSimple(10000),
                10000, peList, new org.cloudbus.cloudsim.VmSchedulerTimeShared(peList)
            );
            List<org.cloudbus.cloudsim.Host> hostList = new ArrayList<>();
            hostList.add(host);

            // Minimal FogDeviceCharacteristics
            org.fog.entities.FogDeviceCharacteristics characteristics = new org.fog.entities.FogDeviceCharacteristics(
                "x86", // architecture
                "Linux", // os
                "Xen", // vmm
                host, // single Host, not a list
                2.0, // timeZone
                3.0, // costPerSec
                0.05, // costPerMem
                0.001, // costPerStorage
                0.0 // costPerBw
            );

            // Minimal VmAllocationPolicy
            org.cloudbus.cloudsim.VmAllocationPolicy vmAllocationPolicy = new org.cloudbus.cloudsim.VmAllocationPolicySimple(hostList);

            // Minimal Storage list
            List<org.cloudbus.cloudsim.Storage> storageList = new LinkedList<>();

            // Create a mock FogDevice using the real constructor and override needed methods
            FogDevice device = new FogDevice(
                "cloud1",
                characteristics,
                vmAllocationPolicy,
                storageList,
                1.0, // schedulingInterval
                10000, // uplinkBandwidth
                10000, // downlinkBandwidth
                10.0, // uplinkLatency
                0.01 // ratePerMips
            ) {
                @Override
                public double getUplinkLatency() { return 10.0; }
                @Override
                public double getPower() { return 50.0; }
                @Override
                public int getId() { return 1; }
                @Override
                public String getName() { return "cloud1"; }
                // Not an override, just a helper for reflection
                public double getMips() { return 1000.0; }
            };

            // Minimal CloudletScheduler for AppModule
            org.cloudbus.cloudsim.CloudletScheduler cloudletScheduler = new org.cloudbus.cloudsim.CloudletSchedulerTimeShared();
            // Minimal selectivity map
            java.util.Map<org.apache.commons.math3.util.Pair<String, String>, org.fog.application.selectivity.SelectivityModel> selectivityMap = new java.util.HashMap<>();

            // Create a mock AppModule using the real constructor and override needed methods
            AppModule module = new AppModule(
                1,              // id
                "MockModule",  // name
                "MockApp",     // appId
                1,              // userId
                1000.0,         // mips
                512,            // ram
                1000,           // bw
                10000,          // size
                "Xen",         // vmm
                cloudletScheduler,           // CloudletScheduler
                selectivityMap            // selectivityMap
            ) {
                // Not an override, just a helper for reflection
                public double getMiPerTuple() { return 2000.0; }
            };

            List<FogDevice> devices = new ArrayList<>();
            devices.add(device);

            Map<String, Integer> result = placement.makePlacementDecision(devices, module);
            System.out.println("Placement Decision (detailed):");
            for (Map.Entry<String, Integer> entry : result.entrySet()) {
                System.out.println("Module: " + entry.getKey() + ", Device ID: " + entry.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}