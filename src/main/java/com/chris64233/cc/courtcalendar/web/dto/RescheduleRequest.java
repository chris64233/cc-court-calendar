package com.chris64233.cc.courtcalendar.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 改期请求。必须携带客户端读取详情时获得的版本号，基于过期版本的更新将被拒绝。
 */
public record RescheduleRequest(
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @NotNull Long expectedVersion) {
}
