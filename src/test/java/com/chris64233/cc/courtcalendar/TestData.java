package com.chris64233.cc.courtcalendar;

import com.chris64233.cc.courtcalendar.web.dto.CourtroomRequest;
import com.chris64233.cc.courtcalendar.web.dto.HearingRequest;
import com.chris64233.cc.courtcalendar.web.dto.JudgeRequest;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantRequest;
import java.time.LocalDateTime;
import java.util.Set;

/** 测试请求构造工具。 */
public final class TestData {

    public static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private TestData() {
    }

    public static JudgeRequest judge(String name) {
        return new JudgeRequest(name);
    }

    public static ParticipantRequest participant(String name) {
        return new ParticipantRequest(name);
    }

    public static CourtroomRequest courtroom(String name, int capacity, String... facilities) {
        return new CourtroomRequest(name, capacity,
                facilities.length == 0 ? Set.of() : Set.of(facilities));
    }

    public static HearingRequest hearing(String caseNo, int people, Set<String> facilities,
                                         Long judgeId, Long courtroomId, Set<Long> participantIds,
                                         LocalDateTime start, LocalDateTime end) {
        return new HearingRequest(caseNo, people, facilities, judgeId, courtroomId,
                participantIds, start, end);
    }
}
