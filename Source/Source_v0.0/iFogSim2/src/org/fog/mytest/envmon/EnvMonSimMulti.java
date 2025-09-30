package org.fog.mytest.envmon;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
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
import org.cloudbus.cloudsim.power.models.PowerModelLinear;  // add this import


import java.util.*;

/**
 * Multi-node demo: 1 Cloud + 2 Fog gateways + 1 AQI sensor + 1 Display actuator.
 * Module "data_processor" is placed on the CLOUD to force network usage
 * and cloud energy consumption.
 */
public class EnvMonSimMulti {

    // Global registries required by Controller
    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();

    public static void main(String[] args) {
        Log.printLine("Starting EnvMonSimMulti...");

        try {
            // ------------------------------------------------------------
            // 1) Initialize CloudSim
            // ------------------------------------------------------------
            CloudSim.init(1, Calendar.getInstance(), false);
            FogBroker broker = new FogBroker("broker");

            // ------------------------------------------------------------
            // 2) Build topology: Cloud + Fog-1 + Fog-2
            // ------------------------------------------------------------
            FogDevice cloud = createFogDevice("cloud",
                    44800 /* MIPS */, 40000 /* RAM */,
                    100 /* upBw */, 10000 /* downBw */,
                    0.01 /* ratePerMips */);

            cloud.setParentId(-1); // root

            FogDevice fog1 = createFogDevice("fog-1",
                    2800, 4000,
                    1000, 10000,
                    0.0);

            fog1.setParentId(cloud.getId());
            fog1.setUplinkLatency(4.0); // latency up to cloud

            FogDevice fog2 = createFogDevice("fog-2",
                    2800, 4000,
                    1000, 10000,
                    0.0);

            fog2.setParentId(cloud.getId());
            fog2.setUplinkLatency(6.0);

            fogDevices.add(cloud);
            fogDevices.add(fog1);
            fogDevices.add(fog2);

            // ------------------------------------------------------------
            // 3) Application: Sensor ("AQI") -> data_processor -> Actuator ("DISPLAY")
            // ------------------------------------------------------------
            Application app = Application.createApplication("EnvMonAppMulti", broker.getId());

            // One processing module
            app.addAppModule("data_processor", 20 /* MIPS requirement for module */);

            // Edges: Sensor -> Module (UP), Module -> Actuator (DOWN)
            app.addAppEdge("AQI", "data_processor",
                    1000 /* tuple size */, 2000 /* output size */,
                    "AQI", Tuple.UP, AppEdge.SENSOR);
            app.addAppEdge("data_processor", "DISPLAY",
                    500 /* tuple size */, 500 /* output size */,
                    "DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);

            // Map input->output within module
            app.addTupleMapping("data_processor", "AQI", "DISPLAY", new FractionalSelectivity(1.0));

            // ------------------------------------------------------------
            // 4) Create and attach one Sensor + one Actuator to Fog-1
            // ------------------------------------------------------------
            Sensor sAqi = new Sensor("s-AQI", "AQI", broker.getId(), app.getAppId(),
                    new DeterministicDistribution(5)); // emits every 5 time units
            sAqi.setGatewayDeviceId(fog1.getId());
            sAqi.setLatency(1.0);

            Actuator aDisplay = new Actuator("a-Display", broker.getId(), app.getAppId(), "DISPLAY");
            aDisplay.setGatewayDeviceId(fog1.getId());
            aDisplay.setLatency(1.0);

            sensors.add(sAqi);
            actuators.add(aDisplay);

            // ------------------------------------------------------------
            // 5) Placement: force module to CLOUD to get network & cloud energy
            //    (Try moving to fog-1 to compare results later)
            // ------------------------------------------------------------
            ModuleMapping mapping = ModuleMapping.createModuleMapping();
            mapping.addModuleToDevice("data_processor", cloud.getName());

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(app, new ModulePlacementMapping(fogDevices, app, mapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            // ------------------------------------------------------------
            // 6) Run
            // ------------------------------------------------------------
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("EnvMonSimMulti finished!");
        } catch (Throwable t) {
            t.printStackTrace();
            Log.printLine("Unexpected error in EnvMonSimMulti.");
        }
    }

    /**
     * Create a FogDevice compatible with your repo’s FogDeviceCharacteristics
     * (single Host, 9-arg constructor).
     */
    private static FogDevice createFogDevice(String nodeName,
                                             long mips, int ram,
                                             long upBw, long downBw,
                                             double ratePerMips) {
        try {
            // One PE
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(mips)));

            int hostId = FogUtils.generateEntityId();
            long storage = 1_000_000; // 1 GB
            int bw = 10_000;

            // Build a single Host
            Host host = new PowerHost(
                    hostId,
                    new RamProvisionerSimple(ram),
                    new BwProvisionerSimple(bw),
                    storage,
                    peList,
                    new VmSchedulerTimeShared(peList),
                    new PowerModelLinear(50, 150)   // FIX: non-null power model
            );


            List<Host> hostList = new ArrayList<>();
            hostList.add(host);

            // IMPORTANT: Your FogDeviceCharacteristics expects a SINGLE Host + 9 args
            FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                    "x86",          // architecture
                    "Linux",        // OS
                    "Xen",          // VMM
                    host,           // SINGLE host (not a list)
                    10.0,           // time zone
                    3.0,            // cost per second
                    0.05,           // cost per memory
                    0.001,          // cost per storage
                    0.0             // cost per bandwidth
            );

            // Build the FogDevice
            return new FogDevice(
                    nodeName,
                    characteristics,
                    new VmAllocationPolicySimple(hostList),
                    new LinkedList<Storage>(),
                    10.0,            // scheduling interval
                    upBw,
                    downBw,
                    0.0,             // ratePerMips (link cost). Keep 0.0 here.
                    ratePerMips      // cpu cost per MIPS
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create FogDevice " + nodeName, e);
        }
    }
}
