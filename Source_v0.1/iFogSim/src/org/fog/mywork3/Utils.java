package org.fog.mywork3;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.provisioners.*;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.utils.FogUtils;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;

import java.util.*;

public class Utils {
    public static FogDevice createFogDevice(String name, long mips, int ram, long upBw, long downBw, double ratePerMips, double busyPower, double idlePower) throws Exception {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // just large number
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerTimeShared(peList),
                new PowerModelLinear(busyPower, idlePower)
        );
        List<Host> hostList = new ArrayList<>(); hostList.add(host);

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                "x86", "Linux", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0);

        FogDevice device = new FogDevice(
            name,
            characteristics,
            new VmAllocationPolicySimple(hostList),
            new ArrayList<Storage>(), // empty storage list
            1.0, // scheduling interval
            upBw,
            downBw,
            0, // uplink latency (default to 0, or set as needed)
            ratePerMips
        );
        return device;
    }
}