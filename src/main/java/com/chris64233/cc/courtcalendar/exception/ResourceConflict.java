package com.chris64233.cc.courtcalendar.exception;

/**
 * 排期/改期校验中发现的单个资源冲突。
 *
 * @param resourceType        资源类型：judge / courtroom / participant
 * @param resourceId          资源 ID
 * @param resourceName        资源名称
 * @param reason              冲突原因
 * @param conflictingCase     与已有庭审冲突时，对方案件号（其余原因为 null）
 * @param detail              人类可读的冲突说明（不含内部异常信息）
 */
public record ResourceConflict(
        String resourceType,
        Long resourceId,
        String resourceName,
        ConflictReason reason,
        String conflictingCase,
        String detail) {
}
