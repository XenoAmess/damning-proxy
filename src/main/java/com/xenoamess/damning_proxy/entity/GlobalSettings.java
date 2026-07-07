package com.xenoamess.damning_proxy.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "global_settings")
public class GlobalSettings extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "max_requests_per_window", nullable = false)
    public Integer maxRequestsPerWindow = 60;

    @Column(name = "window_seconds", nullable = false)
    public Integer windowSeconds = 60;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public GlobalSettings() {
    }

    public static GlobalSettings defaults() {
        GlobalSettings settings = new GlobalSettings();
        settings.maxRequestsPerWindow = 60;
        settings.windowSeconds = 60;
        return settings;
    }
}
