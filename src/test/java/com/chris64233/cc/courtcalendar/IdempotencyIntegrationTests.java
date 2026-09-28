package com.chris64233.cc.courtcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.cc.courtcalendar.api.HearingResponse;
import com.chris64233.cc.courtcalendar.api.RescheduleRequest;
import com.chris64233.cc.courtcalendar.api.ScheduleHearingRequest;
import com.chris64233.cc.courtcalendar.exception.IdempotencyConflictException;
import com.chris64233.cc.courtcalendar.service.HearingService;

/**
 * 幂等键：相同键 + 相同内容返回原结果；相同键 + 不同内容冲突。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:db-idem;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class IdempotencyIntegrationTests {

    @Autowired
    private HearingService hearingService;
    @Autowired
    private TestFixtures fixtures;

    private ScheduleHearingRequest request(String caseNumber, String hashTag) {
        Long judge = fixtures.judge("法官-" + hashTag);
        Long room = fixtures.courtroom("法庭-" + hashTag, 10, "标准");
        Long p = fixtures.participant("参与人-" + hashTag);
        return fixtures.hearing(caseNumber, 3, Set.of("标准"), judge, room, List.of(p),
                "2026-12-01T09:00:00", "2026-12-01T10:00:00");
    }

    @Test
    void sameKeyAndSameContentReplaysOriginalResult() {
        ScheduleHearingRequest first = request("CASE-IDEM-1", "idem1");
        HearingResponse created = hearingService.schedule(first, "key-1", "content-hash-A");

        // 同键同内容重放：返回首次结果（同一 ID、版本仍为 0），不生成第二条排期
        HearingResponse replay = hearingService.schedule(first, "key-1", "content-hash-A");
        assertThat(replay).isEqualTo(created);
        assertThat(hearingService.getSchedule("judge", first.judgeId())).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentContentIsConflict() {
        ScheduleHearingRequest first = request("CASE-IDEM-2", "idem2");
        hearingService.schedule(first, "key-2", "content-hash-A");

        ScheduleHearingRequest different = new ScheduleHearingRequest(
                "CASE-IDEM-2-DIFFERENT", 8, Set.of("标准", "显示屏"),
                first.judgeId(), first.courtroomId(), first.participantIds(),
                java.time.LocalDateTime.parse("2026-12-02T09:00:00"),
                java.time.LocalDateTime.parse("2026-12-02T11:00:00"));

        assertThatThrownBy(() -> hearingService.schedule(different, "key-2", "content-hash-B"))
                .isInstanceOf(IdempotencyConflictException.class);

        // 冲突的重放没有产生任何排期
        assertThat(hearingService.getSchedule("judge", first.judgeId())).hasSize(1);
    }

    @Test
    void rescheduleReplayDoesNotCreateDuplicateOccupancy() {
        ScheduleHearingRequest first = request("CASE-IDEM-3", "idem3");
        hearingService.schedule(first, "key-3", "content-hash-A");

        RescheduleRequest move = new RescheduleRequest(0L,
                java.time.LocalDateTime.parse("2026-12-01T14:00:00"),
                java.time.LocalDateTime.parse("2026-12-01T15:00:00"));
        HearingResponse moved = hearingService.reschedule("CASE-IDEM-3", move,
                "key-3-move", "move-hash-A");
        assertThat(moved.version()).isEqualTo(1);

        // 同键同内容重放改期：返回原结果，版本不再增加，不产生重复占用
        HearingResponse replay = hearingService.reschedule("CASE-IDEM-3", move,
                "key-3-move", "move-hash-A");
        assertThat(replay).isEqualTo(moved);
        assertThat(replay.version()).isEqualTo(1);

        // 该法官当天只有这一场庭
        assertThat(hearingService.getSchedule("judge", first.judgeId()))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.caseNumber()).isEqualTo("CASE-IDEM-3");
                    assertThat(h.start()).isEqualTo("2026-12-01T14:00");
                });
    }
}
