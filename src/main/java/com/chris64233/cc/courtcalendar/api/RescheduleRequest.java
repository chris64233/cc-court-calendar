package com.chris64233.cc.courtcalendar.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 原子改期请求。
 *
 * @param version 客户端读取详情时拿到的版本号；与当前版本不一致则拒绝
 */
public record RescheduleRequest(
        @NotNull(message = "版本号不能为空")
        @PositiveOrZero(message = "版本号不能为负数")
        Long version,

        @NotNull(message = "新开始时间不能为空")
        LocalDateTime start,

        @NotNull(message = "新结束时间不能为空")
        LocalDateTime end) {
}
