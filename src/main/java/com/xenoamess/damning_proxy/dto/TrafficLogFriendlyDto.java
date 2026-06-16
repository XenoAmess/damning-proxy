package com.xenoamess.damning_proxy.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TrafficLogFriendlyDto {

    public Long id;
    public Long instanceId;
    public Long profileId;
    public String requestMethod;
    public String requestPath;
    public LocalDateTime requestTime;
    public Integer responseStatus;
    public Long durationMs;
    public LocalDateTime responseTime;

    public Object requestBody;
    public Object responseBody;

    public String userPrompt;
    public String modelOutput;
    public String model;

    public List<PluginExecutionSnapshot> requestPipeline = new ArrayList<>();
    public List<PluginExecutionSnapshot> responsePipeline = new ArrayList<>();

    public String rawRequestHeaders;
    public String rawResponseHeaders;
    public String rawPluginLogs;
}
