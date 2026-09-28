package com.chris64233.cc.courtcalendar.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 创建庭审排期请求。
 *
 * @param caseNo              案件号（全局唯一）
 * @param expectedPeople      预计人数
 * @param requiredFacilities  所需设施集合
 * @param judgeId             法官 id
 * @param courtroomId         法庭 id
 * @param participantIds      全部参与人 id
 * @param startAt             开始时间（含）
 * @param endAt               结束时间（不含）
 */
public record HearingRequest(
        @NotBlank @Size(max = 64) String caseNo,
        @Min(1) int expectedPeople,
        Set<@NotBlank @Size(max = 64) String> requiredFacilities,
        @NotNull Long judgeId,
        @NotNull Long courtroomId,
        @NotEmpty Set<@NotNull Long> participantIds,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt) {
}
