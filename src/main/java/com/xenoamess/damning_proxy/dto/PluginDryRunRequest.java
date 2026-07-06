package com.xenoamess.damning_proxy.dto;

import com.xenoamess.damning_proxy.entity.Plugin;

public class PluginDryRunRequest {

    public Plugin.ExecutionPhase phase;
    public Long instanceId;

    public Object requestBody;
    public java.util.Map<String, String> requestHeaders;

    public Object responseBody;
    public java.util.Map<String, String> responseHeaders;
    public Integer responseStatus;

    public PluginDryRunRequest() {
    }
}
