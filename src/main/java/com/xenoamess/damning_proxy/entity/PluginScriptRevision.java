package com.xenoamess.damning_proxy.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "plugin_script_revision")
public class PluginScriptRevision extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "plugin_id", nullable = false)
    public Long pluginId;

    @Column(nullable = false, length = 10000)
    public String script;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "created_by", length = 200)
    public String createdBy;

    public PluginScriptRevision() {
    }

    public PluginScriptRevision(Long pluginId, String script) {
        this.pluginId = pluginId;
        this.script = script;
    }
}
