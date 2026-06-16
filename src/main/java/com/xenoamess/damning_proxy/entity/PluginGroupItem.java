package com.xenoamess.damning_proxy.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "plugin_group_item")
public class PluginGroupItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    @JsonIgnore
    public PluginGroup group;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "plugin_id")
    public Plugin plugin;

    @Column(name = "order_index", nullable = false)
    public Integer orderIndex = 0;

    @Column(nullable = false)
    public Integer priority = 0;

    @Column(nullable = false)
    public boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public PluginGroupItem() {
    }
}
