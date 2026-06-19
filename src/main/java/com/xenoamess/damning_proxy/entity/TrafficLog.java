package com.xenoamess.damning_proxy.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_log")
public class TrafficLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "instance_id")
    public Long instanceId;

    @Column(name = "instance_slug")
    public String instanceSlug;

    @Column(name = "profile_id")
    public Long profileId;

    @Column(name = "request_path")
    public String requestPath;

    @Column(name = "request_method")
    public String requestMethod;

    @Column(name = "request_headers", length = 4000)
    public String requestHeaders;

    @Column(name = "request_body", length = 20000)
    public String requestBody;

    @Column(name = "request_body_length")
    public Integer requestBodyLength;

    @Column(name = "upstream_base_url")
    public String upstreamBaseUrl;

    @Column(name = "timeout_ms")
    public Integer timeoutMs;

    @Column(name = "streaming")
    public Boolean streaming;

    @CreationTimestamp
    @Column(name = "request_time")
    public LocalDateTime requestTime;

    @Column(name = "response_status")
    public Integer responseStatus;

    @Column(name = "response_headers", length = 4000)
    public String responseHeaders;

    @Column(name = "response_body", length = 20000)
    public String responseBody;

    @Column(name = "response_body_length")
    public Integer responseBodyLength;

    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    @Column(name = "response_time")
    public LocalDateTime responseTime;

    @Column(name = "duration_ms")
    public Long durationMs;

    @Column(name = "plugin_logs", length = 10000)
    public String pluginLogs;

    @Column(name = "friendly_plugin_snapshots", length = 10000)
    public String friendlyPluginSnapshots;

    public TrafficLog() {
    }
}
