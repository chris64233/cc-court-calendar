package com.chris64233.cc.courtcalendar.api;

import java.time.LocalDateTime;
import java.util.List;

import com.chris64233.cc.courtcalendar.exception.ResourceConflict;

/**
 * 统一错误响应体。只暴露业务可读信息，不包含堆栈、SQL 等内部细节。
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ResourceConflict> conflicts) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path,
                                   List<ResourceConflict> conflicts) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, conflicts);
    }
}
