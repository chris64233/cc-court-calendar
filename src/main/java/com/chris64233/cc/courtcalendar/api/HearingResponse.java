package com.chris64233.cc.courtcalendar.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 庭审排期详情 / 日程条目。
 */
public record HearingResponse(
        Long id,
        String caseNumber,
        int expectedAttendees,
        Set<String> requiredFacilities,
        Long judgeId,
        String judgeName,
        Long courtroomId,
        String courtroomName,
        List<ParticipantRef> participants,
        LocalDateTime start,
        LocalDateTime end,
        long version) {
}
