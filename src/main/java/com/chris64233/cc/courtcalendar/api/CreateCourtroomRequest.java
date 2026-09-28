package com.chris64233.cc.courtcalendar.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateCourtroomRequest(
        @NotBlank(message = "法庭名称不能为空")
        String name,

        @Positive(message = "法庭容量必须为正数")
        int capacity,

        Set<String> facilities,

        @Valid
        List<TimeRangeDto> unavailableRanges) {

    @Override
    public Set<String> facilities() {
        return facilities == null ? new LinkedHashSet<>() : facilities;
    }

    @Override
    public List<TimeRangeDto> unavailableRanges() {
        return unavailableRanges == null ? new ArrayList<>() : unavailableRanges;
    }
}
