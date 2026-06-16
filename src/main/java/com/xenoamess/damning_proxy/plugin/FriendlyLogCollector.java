package com.xenoamess.damning_proxy.plugin;

import com.xenoamess.damning_proxy.dto.PluginExecutionSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FriendlyLogCollector {

    private final List<PluginExecutionSnapshot> snapshots = new ArrayList<>();

    public void add(String name, String phase, Object input, Object output, boolean error, String log) {
        snapshots.add(new PluginExecutionSnapshot(name, phase, input, output, error, log));
    }

    public List<PluginExecutionSnapshot> getSnapshots() {
        return snapshots;
    }
}
