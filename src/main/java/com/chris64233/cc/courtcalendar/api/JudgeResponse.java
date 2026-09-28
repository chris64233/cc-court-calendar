package com.chris64233.cc.courtcalendar.api;

import java.util.List;

public record JudgeResponse(
        Long id,
        String name,
        List<TimeRangeDto> unavailableRanges) {
}
