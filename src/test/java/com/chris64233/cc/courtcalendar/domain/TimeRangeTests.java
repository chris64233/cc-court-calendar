package com.chris64233.cc.courtcalendar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TimeRangeTests {

    private TimeRange range(String start, String end) {
        return new TimeRange(LocalDateTime.parse(start), LocalDateTime.parse(end));
    }

    @Test
    void halfOpenIntervalsTouchingAtEndpointDoNotOverlap() {
        TimeRange morning = range("2026-01-01T09:00:00", "2026-01-01T10:00:00");
        TimeRange next = range("2026-01-01T10:00:00", "2026-01-01T11:00:00");

        assertThat(morning.overlaps(next)).isFalse();
        assertThat(next.overlaps(morning)).isFalse();
    }

    @Test
    void overlappingIntervalsDetected() {
        assertThat(range("2026-01-01T09:00:00", "2026-01-01T10:30:00")
                .overlaps(range("2026-01-01T10:00:00", "2026-01-01T11:00:00")))
                .isTrue();
    }

    @Test
    void rejectsEmptyOrInvalidInterval() {
        LocalDateTime t = LocalDateTime.parse("2026-01-01T09:00:00");
        assertThatThrownBy(() -> new TimeRange(t, t)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeRange(t, t.minusHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
