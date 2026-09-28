package com.chris64233.cc.courtcalendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CourtroomRequest(
        @NotBlank @Size(max = 128) String name,
        @PositiveOrZero int capacity,
        Set<@NotBlank @Size(max = 64) String> facilities) {
}
