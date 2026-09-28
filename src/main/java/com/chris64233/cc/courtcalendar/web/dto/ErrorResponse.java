package com.chris64233.cc.courtcalendar.web.dto;

import com.chris64233.cc.courtcalendar.error.ConflictDetail;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一错误响应。仅包含安全的业务信息，绝不回传内部异常、堆栈或 SQL。
 */
public record ErrorResponse(
        String code,
        String message,
        List<ConflictDetail> conflicts,
        LocalDateTime timestamp,
        String path) {
}
