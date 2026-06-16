package com.xenoamess.damning_proxy.dto;

public class PluginExecutionSnapshot {

    public String name;
    public String phase;
    public Object input;
    public Object output;
    public boolean error;
    public String log;

    public PluginExecutionSnapshot() {
    }

    public PluginExecutionSnapshot(String name, String phase, Object input, Object output, boolean error, String log) {
        this.name = name;
        this.phase = phase;
        this.input = input;
        this.output = output;
        this.error = error;
        this.log = log;
    }
}
