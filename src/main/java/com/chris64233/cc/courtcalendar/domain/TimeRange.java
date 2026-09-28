package com.chris64233.cc.courtcalendar.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Embeddable;

/**
 * 左闭右开的时间区间 [start, end)。
 */
@Embeddable
public class TimeRange implements Serializable {

    private LocalDateTime start;
    private LocalDateTime end;

    protected TimeRange() {
    }

    public TimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("时间区间的起止时间不能为空");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("时间区间开始时间必须早于结束时间（左闭右开）");
        }
        this.start = start;
        this.end = end;
    }

    /**
     * 两个左闭右开区间是否重叠（端点相接不算冲突）。
     */
    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeRange that)) {
            return false;
        }
        return Objects.equals(start, that.start) && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }
}
