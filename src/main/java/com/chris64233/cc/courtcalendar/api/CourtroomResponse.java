package com.chris64233.cc.courtcalendar.api;

import java.util.List;
import java.util.Set;

public record CourtroomResponse(
        Long id,
        String name,
        int capacity,
        Set<String> facilities,
        List<TimeRangeDto> unavailableRanges) {
}
