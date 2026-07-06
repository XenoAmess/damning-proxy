package com.xenoamess.damning_proxy.dto;

import java.util.List;
import java.util.Map;

public class PluginDryRunResponse {

    public String pluginName;
    public String phase;

    public Object input;
    public Object output;
    public boolean error;
    public String errorMessage;

    public List<String> pluginLogs;

    public Object requestBody;
    public Map<String, String> requestHeaders;

    public Object responseBody;
    public Map<String, String> responseHeaders;
    public Integer responseStatus;

    public boolean stopped;
    public boolean returned;

    public PluginDryRunResponse() {
    }
}
