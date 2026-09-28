package com.chris64233.cc.courtcalendar.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * 左闭右开时间区间 [start, end)。
 */
public record TimeRangeDto(
        @NotNull(message = "开始时间不能为空")
        LocalDateTime start,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime end) {
}
