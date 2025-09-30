package org.fog.work1;

import java.util.*;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModel;

import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.application.AppModule;

import org.fog.entities.FogDevice;
import org.fog.entities.Tuple;
import org.fog.entities.PlacementRequest;       // NOTE: in iFogSim2 it's under entities

import org.fog.placement.MicroservicePlacementLogic; // your interface
import org.fog.placement.PlacementLogicOutput;
import org.apache.commons.math3.util.Pair;
import org.fog.utils.ModuleLaunchConfig;

/**
 * Adaptive microservice placement policy for iFogSim2.
 * Matches your interface: run(...) + postProcessing().
 * Returns mapping: moduleName -> deviceId
 */
public class AdaptiveMicroservicePlacement implements MicroservicePlacementLogic {

    private final String appId;

    // J = alpha*latency + beta*energy + gamma*cost
    private double alpha = 0.6;
    private double beta  = 0.3;
    private double gamma = 0.1;
    private double hysteresis = 1.0;

    public AdaptiveMicroservicePlacement(String appId) {
        this.appId = appId;
    }

    public void setWeights(double a, double b, double g) {
        this.alpha = a; this.beta = b; this.gamma = g;
    }

    /**
     * REQUIRED by your interface. Decide the placement and return a mapping.
     *
     * @param fogDevices   list of candidate devices
     * @param applications map of all apps; we pick ours via appId
     * @param metrics      per-user metrics (if any). Not used here.
     * @param requests     outstanding placement requests. Not used here.
     */
    @Override
    public PlacementLogicOutput run(
            List<FogDevice> fogDevices,
            Map<String, Application> applications,
            Map<Integer, Map<String, Double>> metrics,
            List<PlacementRequest> requests) {

        Map<String, Integer> mapping = new HashMap<>();
        final String moduleName = "processing";

        Application app = (applications != null) ? applications.get(appId) : null;
        if (app == null || fogDevices == null || fogDevices.isEmpty()) {
            // Return empty PlacementLogicOutput
            return new PlacementLogicOutput(new HashMap<>(), new HashMap<>(), new HashMap<>());
        }

        FogDevice best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (FogDevice d : fogDevices) {
            double latMs = estimateLatencyMs(d, app, moduleName);
            double eneJ  = estimateEnergyJ(d, app, moduleName);
            double cost  = estimateCost(d, app, moduleName);

            double score = alpha*latMs + beta*eneJ + gamma*cost;
            if (score + hysteresis < bestScore) {
                bestScore = score;
                best = d;
            }
        }

        Map<Integer, Map<Application, List<ModuleLaunchConfig>>> perDevice = new HashMap<>();
        Map<Integer, List<Pair<String, Integer>>> serviceDiscoveryInfo = new HashMap<>();
        Map<PlacementRequest, Integer> prStatus = new HashMap<>();

        if (best != null) {
            // Find the AppModule for 'processing'
            AppModule processingModule = null;
            for (AppModule m : app.getModules()) {
                if (moduleName.equals(m.getName())) {
                    processingModule = m;
                    break;
                }
            }
            if (processingModule != null) {
                List<ModuleLaunchConfig> moduleList = new ArrayList<>();
                moduleList.add(new ModuleLaunchConfig(processingModule, 1));
                Map<Application, List<ModuleLaunchConfig>> appModules = new HashMap<>();
                appModules.put(app, moduleList);
                perDevice.put(best.getId(), appModules);
            }
            // Optionally, add serviceDiscoveryInfo and prStatus if needed
        }
        return new PlacementLogicOutput(perDevice, serviceDiscoveryInfo, prStatus);
    }

    /** REQUIRED by your interface. Keep as no-op unless you want to report metrics. */
    @Override
    public void postProcessing() {
        // e.g., System.out.println("Post-processing done for app: " + appId);
    }

    /** REQUIRED by your interface. No-op implementation. */
    @Override
    public void updateResources(Map<Integer, Map<String, Double>> resourceAvailability) {
        // No-op for now
    }

    /* ------------------------------ Internals ------------------------------ */

    private double estimateLatencyMs(FogDevice device, Application app, String moduleName) {
        return safeUplinkLatency(device) + estimateProcessingMs(device, app, moduleName);
    }

    /**
     * Processing time = incoming tuple MI / host total MIPS.
     * MI is read from the upstream AppEdge feeding the module (SENSOR -> module, direction UP),
     * which is the same field Sensor.java uses when creating tuples.  [4](https://www.youtube.com/watch?v=ofWEGI_LqCw)
     */
    private double estimateProcessingMs(FogDevice device, Application app, String moduleName) {
        double mi = incomingEdgeMi(app, moduleName);
        double hostMips = totalHostMips(device);
        if (mi <= 0 || hostMips <= 0) return 1000.0;
        return (mi / hostMips) * 1000.0;
    }

    /** Energy ≈ power(avgUtil) * processingTime; avgUtil ≈ min(1, MI / hostMIPS). */
    private double estimateEnergyJ(FogDevice device, Application app, String moduleName) {
        double procMs = estimateProcessingMs(device, app, moduleName);
        double hostMips = totalHostMips(device);
        if (procMs <= 0 || hostMips <= 0) return 0.0;

        double mi = incomingEdgeMi(app, moduleName);
        double avgUtil = Math.min(1.0, mi / Math.max(1.0, hostMips));

        PowerModel pm = firstPowerModel(device);
        double pW;
        try { pW = (pm != null) ? pm.getPower(avgUtil) : 100.0; }
        catch (Exception e) { pW = 100.0; }
        return pW * (procMs / 1000.0);
    }

    /** Example tariff: only cloud nodes incur cost (customize for your study). */
    private double estimateCost(FogDevice device, Application app, String moduleName) {
        if (!isCloudDevice(device)) return 0.0;
        double mi = incomingEdgeMi(app, moduleName);
        return (mi / 1e6) * 0.01; // $0.01 per M-instruction (illustrative)
    }

    private boolean isCloudDevice(FogDevice d) {
        return d != null && d.getName() != null && d.getName().toLowerCase().contains("cloud");
    }

    /** Read MI from AppEdge feeding the module (dest==moduleName, direction==Tuple.UP).  [4](https://www.youtube.com/watch?v=ofWEGI_LqCw) */
    private double incomingEdgeMi(Application app, String moduleName) {
        double mi = 0.0;
        for (AppEdge e : app.getEdges()) {
            if (moduleName.equals(e.getDestination()) && e.getDirection() == Tuple.UP) {
                mi = Math.max(mi, e.getTupleCpuLength());
            }
        }
        return mi;
    }

    /** Sum PE MIPS across device hosts (matches iFogSim placement internals).  [3](https://deepai.org/publication/ifogsim2-an-extended-ifogsim-simulator-for-mobility-clustering-and-microservice-management-in-edge-and-fog-computing-environments) */
    private double totalHostMips(FogDevice device) {
        if (device == null || device.getHostList() == null) return 0.0;
        double sum = 0.0;
        for (Host h : device.getHostList()) {
            if (h == null || h.getPeList() == null) continue;
            for (Pe pe : h.getPeList()) {
                if (pe.getPeProvisioner() != null) sum += pe.getPeProvisioner().getMips();
            }
        }
        return sum;
    }

    private double safeUplinkLatency(FogDevice device) {
        try { return device.getUplinkLatency(); } catch (Exception e) { return 0.0; }
    }

    private PowerModel firstPowerModel(FogDevice device) {
        if (device == null || device.getHostList() == null) return null;
        for (Host h : device.getHostList()) {
            if (h instanceof PowerHost) return ((PowerHost) h).getPowerModel();
        }
        return null;
    }
}