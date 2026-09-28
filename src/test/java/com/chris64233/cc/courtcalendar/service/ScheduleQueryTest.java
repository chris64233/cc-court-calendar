package com.chris64233.cc.courtcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.cc.courtcalendar.TestData;
import com.chris64233.cc.courtcalendar.domain.ResourceType;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomResponse;
import com.chris64233.cc.courtcalendar.web.dto.HearingResponse;
import com.chris64233.cc.courtcalendar.web.dto.JudgeResponse;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScheduleQueryTest {

    @Autowired
    private SchedulingService service;

    private static final LocalDateTime T = TestData.T0;

    @Test
    void scheduleByResourceIsStableSortedAndWindowFiltered() {
        JudgeResponse judge = service.registerJudge("SJ-" + System.nanoTime());
        CourtroomResponse room = service.registerCourtroom(
                "SR-" + System.nanoTime(), 10, Set.of("RECORDING"));
        ParticipantResponse p = service.registerParticipant("SP-" + System.nanoTime());

        // 乱序插入：11:00、09:00、13:00，且让两场开始时间相同验证次级排序。
        schedule("SCH-C", judge, room, p, T.plusHours(4), T.plusHours(5));
        schedule("SCH-A", judge, room, p, T, T.plusHours(1));
        schedule("SCH-B", judge, room, p, T.plusHours(2), T.plusHours(3));

        List<HearingResponse> judgeDay = service.getSchedule(
                ResourceType.JUDGE, judge.id(), T.minusHours(1), T.plusHours(8));
        assertThat(judgeDay).extracting(HearingResponse::caseNo)
                .containsExactly("SCH-A", "SCH-B", "SCH-C");

        // 法庭日程同样稳定排序。
        List<HearingResponse> roomDay = service.getSchedule(
                ResourceType.COURTROOM, room.id(), T.minusHours(1), T.plusHours(8));
        assertThat(roomDay).extracting(HearingResponse::caseNo)
                .containsExactly("SCH-A", "SCH-B", "SCH-C");

        // 参与人日程同样稳定排序。
        List<HearingResponse> participantDay = service.getSchedule(
                ResourceType.PARTICIPANT, p.id(), T.minusHours(1), T.plusHours(8));
        assertThat(participantDay).extracting(HearingResponse::caseNo)
                .containsExactly("SCH-A", "SCH-B", "SCH-C");

        // 查询窗口只取与窗口重叠的庭审：[10:30, 12:30) 只含 SCH-B(11:00-12:00)。
        List<HearingResponse> windowed = service.getSchedule(
                ResourceType.JUDGE, judge.id(), T.plusHours(1).plusMinutes(30),
                T.plusHours(3).plusMinutes(30));
        assertThat(windowed).extracting(HearingResponse::caseNo).containsExactly("SCH-B");

        // 详情查询。
        HearingResponse detail = service.getByCaseNo("SCH-A");
        assertThat(detail.judgeId()).isEqualTo(judge.id());
        assertThat(detail.courtroomId()).isEqualTo(room.id());
        assertThat(detail.participants()).extracting(ParticipantResponse::id)
                .containsExactly(p.id());
    }

    private void schedule(String caseNo, JudgeResponse j, CourtroomResponse r,
                          ParticipantResponse p, LocalDateTime s, LocalDateTime e) {
        service.schedule(TestData.hearing(caseNo, 5, Set.of("RECORDING"),
                j.id(), r.id(), Set.of(p.id()), s, e), null);
    }
}
