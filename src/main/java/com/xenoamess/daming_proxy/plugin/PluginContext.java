package com.xenoamess.daming_proxy.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginContext {

    private final Map<String, String> requestHeaders = new HashMap<>();
    private Object requestBody;

    private final Map<String, String> responseHeaders = new HashMap<>();
    private Object responseBody;
    private int responseStatus = 200;

    private final List<String> pluginLogs = new ArrayList<>();

    private boolean stopped = false;
    private boolean returned = false;

    public PluginContext() {
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public String getRequestHeader(String key) {
        return requestHeaders.get(key);
    }

    public void setRequestHeader(String key, String value) {
        requestHeaders.put(key, value);
    }

    public Object getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(Object requestBody) {
        this.requestBody = requestBody;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public String getResponseHeader(String key) {
        return responseHeaders.get(key);
    }

    public void setResponseHeader(String key, String value) {
        responseHeaders.put(key, value);
    }

    public Object getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(Object responseBody) {
        this.responseBody = responseBody;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public List<String> getPluginLogs() {
        return pluginLogs;
    }

    public void log(String message) {
        pluginLogs.add(message);
    }

    public void stop() {
        this.stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void returnResponse(int status, Object body, Map<String, String> headers) {
        this.returned = true;
        this.responseStatus = status;
        this.responseBody = body;
        if (headers != null) {
            this.responseHeaders.putAll(headers);
        }
    }

    public boolean isReturned() {
        return returned;
    }
}
