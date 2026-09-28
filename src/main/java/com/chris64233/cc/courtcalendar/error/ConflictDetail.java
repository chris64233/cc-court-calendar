package com.chris64233.cc.courtcalendar.error;

import java.util.List;

/**
 * 单个冲突资源的明细，明确告知调用方是哪类资源、哪一条资源、因什么原因冲突。
 *
 * @param resourceType        资源类型：JUDGE / COURTROOM / PARTICIPANT
 * @param resourceId          资源 id
 * @param resourceName        资源名称（便于展示）
 * @param reason              冲突原因代码
 * @param message             人类可读说明
 * @param missingFacilities   法庭缺少的设施（仅设施冲突时有值）
 * @param conflictingCaseNo   发生时间撞档的对方案件号（仅时间冲突时有值）
 */
public record ConflictDetail(
        String resourceType,
        Long resourceId,
        String resourceName,
        String reason,
        String message,
        List<String> missingFacilities,
        String conflictingCaseNo) {

    public static ConflictDetail of(String resourceType, Long resourceId, String resourceName,
                                    String reason, String message) {
        return new ConflictDetail(resourceType, resourceId, resourceName, reason, message,
                null, null);
    }
}
