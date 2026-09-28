package com.chris64233.cc.courtcalendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 资源的不可用时段，统一存储法官、法庭、参与人的不可用区间（左闭右开）。
 */
@Entity
@Table(name = "unavailable_window", indexes = {
        @Index(name = "idx_unavail_resource", columnList = "resource_type,resource_id,start_at,end_at")
})
public class UnavailableWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 16)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "reason", length = 256)
    private String reason;

    protected UnavailableWindow() {
    }

    public UnavailableWindow(ResourceType resourceType, Long resourceId, LocalDateTime startAt,
                             LocalDateTime endAt, String reason) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public String getReason() {
        return reason;
    }
}
