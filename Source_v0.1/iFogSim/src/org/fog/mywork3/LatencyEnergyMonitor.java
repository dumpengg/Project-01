package org.fog.mywork3;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.fog.application.Application;
import org.fog.placement.Controller;
import org.fog.utils.TimeKeeper;

/**
 * Periodically checks latency and energy estimations and triggers (re-)mapping via AdaptivePlacement.
 * This is a light-weight entity that runs alongside the controller.
 */
public class LatencyEnergyMonitor extends SimEntity {
    private final Controller controller;
    private final Application app;
    private final AdaptivePlacement placement;
    private final double intervalMs;

    private static final int EV_CHECK = 55555;

    public LatencyEnergyMonitor(String name, Controller controller, Application app, AdaptivePlacement placement, double intervalMs) {
        super(name);
        this.controller = controller; this.app = app; this.placement = placement; this.intervalMs = intervalMs;
        CloudSim.addEntity(this);
        schedule(getId(), intervalMs, EV_CHECK);
    }

    @Override
    public void startEntity() { }

    @Override
    public void processEvent(org.cloudbus.cloudsim.core.SimEvent ev) {
        if (ev.getTag() == EV_CHECK) {
            // Derive average loop latency from TimeKeeper (updated by iFogSim)
            double avg = 0.0; int n = 0;
            for (Double v : TimeKeeper.getInstance().getLoopIdToCurrentAverage().values()) { if (v != null) { avg += v; n++; } }
            double avgLatency = n>0 ? avg/n : 0.0;

            // Estimate energy (controller prints exact values at the end; here we approximate by sum of device energy so far if available)
            double energy = controller.getEnergyConsumption(); // in Joules (iFogSim2 Controller often exposes this)
            double estCloudCost = controller.getCloudCost();   // in $ (if available; else 0)
            placement.maybeRemap(avgLatency, energy, estCloudCost);
            schedule(getId(), intervalMs, EV_CHECK);
        }
    }

    @Override
    public void shutdownEntity() {
        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        // Print all application loops, using 0.0 if no value is recorded
        for (org.fog.application.AppLoop loop : app.getLoops()) {
            Double avg = TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loop.getModules());
            System.out.println(loop.getModules() + " ---> " + (avg != null ? avg : 0.0));
        }
        System.out.println("=========================================");
        System.out.println("DEVICE ENERGY CONSUMPTION");
        System.out.println("=========================================");
        // Print energy consumption for each device, always including cloud
        if (controller.getFogDevices() != null) {
            for (org.fog.entities.FogDevice dev : controller.getFogDevices()) {
                System.out.println(dev.getName() + " : Energy Consumed = " + dev.getEnergyConsumption());
            }
        }
        System.out.println("=========================================");
        System.out.println("CLOUD COST");
        System.out.println("=========================================");
        try {
            double cloudCost = controller.getCloudCost();
            System.out.println("Cloud Cost = " + cloudCost);
        } catch (Exception e) {
            System.out.println("Cloud Cost = N/A");
        }
    }
}