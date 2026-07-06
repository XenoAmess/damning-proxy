package com.xenoamess.damning_proxy.plugin;

import com.xenoamess.damning_proxy.dto.PluginExecutionSnapshot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FriendlyLogCollector {

    private final List<PluginExecutionSnapshot> snapshots = new CopyOnWriteArrayList<>();

    public void add(Long pluginId, String name, String phase, Object input, Object output, boolean error, String log) {
        snapshots.add(new PluginExecutionSnapshot(pluginId, name, phase, input, output, error, log));
    }

    public List<PluginExecutionSnapshot> getSnapshots() {
        return snapshots;
    }
}
