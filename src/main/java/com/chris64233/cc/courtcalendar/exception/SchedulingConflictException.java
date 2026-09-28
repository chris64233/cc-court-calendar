package com.chris64233.cc.courtcalendar.exception;

import java.util.List;

/**
 * 排期/改期不满足业务约束。携带所有冲突资源，方便调用方明确知道是谁冲突。
 */
public class SchedulingConflictException extends RuntimeException {

    private final transient List<ResourceConflict> conflicts;

    public SchedulingConflictException(List<ResourceConflict> conflicts) {
        super("排期冲突，涉及 " + conflicts.size() + " 项资源");
        this.conflicts = List.copyOf(conflicts);
    }

    public List<ResourceConflict> getConflicts() {
        return conflicts;
    }
}
