package com.chris64233.cc.courtcalendar.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按资源查询的日程结果。结果按开始时间、结束时间、庭审 id 稳定排序。
 */
public record ScheduleResponse(
        String resourceType,
        Long resourceId,
        LocalDateTime windowStartAt,
        LocalDateTime windowEndAt,
        List<HearingResponse> hearings) {
}
