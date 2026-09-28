package com.chris64233.cc.courtcalendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 幂等记录：保存幂等键对应的请求指纹与已创建的庭审。
 * 相同幂等键 + 相同指纹重放返回原结果；指纹不同返回冲突。
 */
@Entity
@Table(name = "idempotent_request")
public class IdempotentRequest {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "hearing_id", nullable = false)
    private Long hearingId;

    protected IdempotentRequest() {
    }

    public IdempotentRequest(String idempotencyKey, String requestFingerprint, Long hearingId) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.hearingId = hearingId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Long getHearingId() {
        return hearingId;
    }
}
