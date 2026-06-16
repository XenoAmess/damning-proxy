package com.xenoamess.damning_proxy.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "plugin_group")
public class PluginGroup extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String slug;

    @Column(length = 2000)
    public String description;

    @Column(nullable = false)
    public boolean enabled = true;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<PluginGroupItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public PluginGroup() {
    }

    public List<PluginGroupItem> sortedItems() {
        return items.stream()
            .sorted(Comparator.comparingInt((PluginGroupItem i) -> i.orderIndex)
                .thenComparingInt(i -> i.priority)
                .thenComparingLong(i -> i.id == null ? Long.MAX_VALUE : i.id))
            .toList();
    }
}
