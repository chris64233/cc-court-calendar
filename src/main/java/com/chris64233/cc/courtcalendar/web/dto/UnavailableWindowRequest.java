package com.chris64233.cc.courtcalendar.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record UnavailableWindowRequest(
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @Size(max = 256) String reason) {
}
