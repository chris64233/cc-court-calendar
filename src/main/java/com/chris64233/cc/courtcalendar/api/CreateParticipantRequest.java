package com.chris64233.cc.courtcalendar.api;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record CreateParticipantRequest(
        @NotBlank(message = "参与人姓名不能为空")
        String name,

        @Valid
        List<TimeRangeDto> unavailableRanges) {

    @Override
    public List<TimeRangeDto> unavailableRanges() {
        return unavailableRanges == null ? new ArrayList<>() : unavailableRanges;
    }
}
