package com.chris64233.cc.courtcalendar.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HearingResponse(
        Long id,
        String caseNo,
        int expectedPeople,
        List<String> requiredFacilities,
        Long judgeId,
        String judgeName,
        Long courtroomId,
        String courtroomName,
        List<ParticipantResponse> participants,
        LocalDateTime startAt,
        LocalDateTime endAt,
        long version) {
}
