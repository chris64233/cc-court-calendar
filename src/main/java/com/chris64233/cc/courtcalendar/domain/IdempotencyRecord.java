package com.chris64233.cc.courtcalendar.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 幂等键记录：保存首次请求的内容指纹与响应快照。
 *
 * <p>相同幂等键 + 相同内容重放时直接返回原结果；
 * 相同幂等键 + 不同内容返回冲突。</p>
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    /** 请求体规范化后的 SHA-256 指纹。 */
    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    /** 首次成功调用的响应体快照。 */
    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "hearing_id")
    private Long hearingId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash, String responseBody,
                             int httpStatus, Long hearingId, LocalDateTime createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.httpStatus = httpStatus;
        this.hearingId = hearingId;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Long getHearingId() {
        return hearingId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
