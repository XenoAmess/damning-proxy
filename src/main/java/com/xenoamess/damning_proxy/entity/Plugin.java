package com.xenoamess.damning_proxy.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "plugin")
public class Plugin extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(length = 2000)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Language language;

    @Column(nullable = false, length = 10000)
    public String script;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "execution_phase")
    public ExecutionPhase executionPhase = ExecutionPhase.BOTH;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(nullable = false)
    public boolean sample = false;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public Plugin() {
    }

    public enum Language {
        GROOVY,
        JS
    }

    public enum ExecutionPhase {
        REQUEST,
        RESPONSE,
        BOTH
    }
}
