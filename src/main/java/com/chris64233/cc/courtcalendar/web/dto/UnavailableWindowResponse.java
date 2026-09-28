package com.chris64233.cc.courtcalendar.web.dto;

import java.time.LocalDateTime;

public record UnavailableWindowResponse(Long id, String resourceType, Long resourceId,
                                        LocalDateTime startAt, LocalDateTime endAt, String reason) {
}
