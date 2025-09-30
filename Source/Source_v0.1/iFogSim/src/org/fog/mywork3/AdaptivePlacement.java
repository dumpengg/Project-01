package org.fog.mywork3;

import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.placement.ModulePlacement;

import java.util.*;

/**
 * Heuristic adaptive placement: decides fog vs cloud based on simple latency/energy estimates.
 * Note: For live migration at runtime, iFogSim2 supports migration; this class computes an initial mapping
 * and exposes hooks called by LatencyEnergyMonitor to re-map modules if thresholds are violated.
 */
public class AdaptivePlacement extends ModulePlacement {
    private final List<Sensor> sensors;
    private final List<Actuator> actuators;
    private final double alpha, beta, gamma;
    private final double tauL, tauE;

    private final Map<String, Integer> moduleToDevice = new HashMap<>();

    public AdaptivePlacement(List<FogDevice> fogDevices,
                             List<Sensor> sensors,
                             List<Actuator> actuators,
                             Application application,
                             double alpha, double beta, double gamma,
                             double tauL, double tauE) {
        setFogDevices(fogDevices);
        setApplication(application);
        setModuleToDeviceMap(new HashMap<String, List<Integer>>());
        setDeviceToModuleMap(new HashMap<Integer, List<org.fog.application.AppModule>>());
        this.sensors = sensors; this.actuators = actuators;
        this.alpha = alpha; this.beta = beta; this.gamma = gamma;
        this.tauL = tauL; this.tauE = tauE;
        mapModules();
    }

    @Override
    protected void mapModules() {
        // Initial mapping: put env-processor on nearest fog nodes; storage to cloud
        int cloudId = getCloudId();
        Map<String, List<Integer>> moduleToDeviceMap = getModuleToDeviceMap();
        // env-processor on all fog nodes
        for (FogDevice d : getFogDevices()) {
            if (d.getName().startsWith("fog-")) {
                moduleToDeviceMap.computeIfAbsent("env-processor", k -> new ArrayList<>()).add(d.getId());
                moduleToDevice.put("env-processor@"+d.getId(), d.getId());
            }
        }
        // env-storage on cloud
        moduleToDeviceMap.computeIfAbsent("env-storage", k -> new ArrayList<>()).add(cloudId);
    }

    private int getCloudId() {
        return getFogDevices().stream().min(Comparator.comparingInt(FogDevice::getLevel)).get().getId();
    }

    public void maybeRemap(double currentAvgLatency, double currentEnergyJ, double estimatedCloudCost) {
        // A simple heuristic: if latency too high or energy too high, move processor to cloud; else keep at fog.
        double J = alpha*currentAvgLatency + beta*currentEnergyJ + gamma*estimatedCloudCost;
        Map<String, List<Integer>> moduleToDeviceMap = getModuleToDeviceMap();
        if (currentAvgLatency > tauL || currentEnergyJ > tauE) {
            // Move all env-processor to cloud (single instance)
            int cloudId = getCloudId();
            // Clear previous mapping for processor
            List<Integer> procList = moduleToDeviceMap.get("env-processor");
            if (procList != null) procList.clear();
            // Assign only to cloud
            moduleToDeviceMap.computeIfAbsent("env-processor", k -> new ArrayList<>()).add(cloudId);
        } else {
            // Ensure processors are on fogs (edgewards)
            List<Integer> procList = moduleToDeviceMap.get("env-processor");
            if (procList != null) procList.clear();
            for (FogDevice d : getFogDevices()) {
                if (d.getName().startsWith("fog-")) {
                    moduleToDeviceMap.computeIfAbsent("env-processor", k -> new ArrayList<>()).add(d.getId());
                }
            }
        }
    }
}