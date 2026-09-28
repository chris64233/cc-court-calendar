package com.chris64233.cc.courtcalendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JudgeRequest(
        @NotBlank @Size(max = 128) String name) {
}
