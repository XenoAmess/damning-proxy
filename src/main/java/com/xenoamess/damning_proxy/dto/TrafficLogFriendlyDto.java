package com.xenoamess.damning_proxy.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TrafficLogFriendlyDto {

    public Long id;
    public Long instanceId;
    public String instanceSlug;
    public Long profileId;
    public String requestMethod;
    public String requestPath;
    public LocalDateTime requestTime;
    public Integer responseStatus;
    public Long durationMs;
    public LocalDateTime responseTime;

    public Object requestBody;
    public Object responseBody;

    public Integer requestBodyLength;
    public Integer responseBodyLength;
    public String upstreamBaseUrl;
    public Integer timeoutMs;
    public Boolean streaming;
    public String errorMessage;

    public Integer promptTokens;
    public Integer completionTokens;
    public Integer totalTokens;

    public String userPrompt;
    public String modelOutput;
    public String model;

    /**
     * Every message in the request {@code messages} array, in original order.
     * Includes system / developer / user / assistant / tool turns. The frontend
     * renders the full conversation rather than just the first user turn.
     */
    public List<ChatMessage> requestMessages = new ArrayList<>();

    /**
     * Assistant messages produced by the upstream (typically one for non-stream
     * responses, one reconstructed entry for stream responses).
     */
    public List<ChatMessage> responseMessages = new ArrayList<>();

    public List<PluginExecutionSnapshot> requestPipeline = new ArrayList<>();
    public List<PluginExecutionSnapshot> responsePipeline = new ArrayList<>();

    public String rawRequestHeaders;
    public String rawResponseHeaders;
    public String rawPluginLogs;
}
