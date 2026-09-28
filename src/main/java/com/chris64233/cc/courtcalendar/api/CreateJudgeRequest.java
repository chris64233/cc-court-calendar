package com.chris64233.cc.courtcalendar.api;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record CreateJudgeRequest(
        @NotBlank(message = "法官姓名不能为空")
        String name,

        @Valid
        List<TimeRangeDto> unavailableRanges) {

    @Override
    public List<TimeRangeDto> unavailableRanges() {
        return unavailableRanges == null ? new ArrayList<>() : unavailableRanges;
    }
}
