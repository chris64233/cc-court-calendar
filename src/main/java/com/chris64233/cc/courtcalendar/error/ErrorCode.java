package com.chris64233.cc.courtcalendar.error;

/**
 * 对外暴露的业务错误码。错误信息只描述业务事实，不包含堆栈、SQL 等内部细节。
 */
public enum ErrorCode {
    RESOURCE_NOT_FOUND,
    DUPLICATE_CASE,
    SCHEDULE_CONFLICT,
    IDEMPOTENCY_CONFLICT,
    VERSION_CONFLICT,
    VALIDATION_ERROR,
    DUPLICATE_NAME
}
