package com.xenoamess.damning_proxy.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "proxy_profile")
public class ProxyProfile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String slug;

    @Column(nullable = false, name = "base_url")
    public String baseUrl;

    @Column(name = "bearer_token")
    public String bearerToken;

    @Column(name = "custom_headers", length = 4000)
    public String customHeaders;

    @Column(name = "custom_body", length = 10000)
    public String customBody;

    @Column(name = "default_model")
    public String defaultModel;

    @Column(name = "timeout_ms")
    public Integer timeoutMs = 600000;

    @Column(nullable = false)
    public boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public ProxyProfile() {
    }

    public ProxyProfile(String name, String slug, String baseUrl) {
        this.name = name;
        this.slug = slug;
        this.baseUrl = baseUrl;
    }
}
