package com.chris64233.cc.courtcalendar.api;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 庭审排期请求：一次同时占用法官、法庭和全部参与人。
 */
public record ScheduleHearingRequest(
        @NotBlank(message = "案件号不能为空")
        String caseNumber,

        @Positive(message = "预计人数必须为正数")
        int expectedAttendees,

        Set<String> requiredFacilities,

        @NotNull(message = "法官 ID 不能为空")
        Long judgeId,

        @NotNull(message = "法庭 ID 不能为空")
        Long courtroomId,

        @NotEmpty(message = "至少需要一名参与人")
        List<Long> participantIds,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime start,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime end) {

    @Override
    public Set<String> requiredFacilities() {
        return requiredFacilities == null ? new LinkedHashSet<>() : requiredFacilities;
    }
}
