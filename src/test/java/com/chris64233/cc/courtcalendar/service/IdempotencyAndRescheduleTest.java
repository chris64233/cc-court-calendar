package com.chris64233.cc.courtcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.cc.courtcalendar.TestData;
import com.chris64233.cc.courtcalendar.error.BusinessException;
import com.chris64233.cc.courtcalendar.error.ErrorCode;
import com.chris64233.cc.courtcalendar.repo.HearingRepository;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomResponse;
import com.chris64233.cc.courtcalendar.web.dto.HearingRequest;
import com.chris64233.cc.courtcalendar.web.dto.HearingResponse;
import com.chris64233.cc.courtcalendar.web.dto.JudgeResponse;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantResponse;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IdempotencyAndRescheduleTest {

    @Autowired
    private SchedulingService service;
    @Autowired
    private HearingRepository hearingRepository;

    private static final LocalDateTime T = TestData.T0;

    private Fixture setup() {
        JudgeResponse j = service.registerJudge("J-" + System.nanoTime());
        CourtroomResponse r = service.registerCourtroom(
                "R-" + System.nanoTime(), 10, Set.of("RECORDING"));
        ParticipantResponse p = service.registerParticipant("P-" + System.nanoTime());
        return new Fixture(j, r, p);
    }

    private HearingRequest hearing(String caseNo, Fixture f, LocalDateTime s, LocalDateTime e) {
        return TestData.hearing(caseNo, 5, Set.of("RECORDING"), f.j().id(), f.r().id(),
                Set.of(f.p().id()), s, e);
    }

    // ---------------- 幂等 ----------------

    @Test
    void sameIdempotencyKeySameContentReturnsOriginal() {
        Fixture f = setup();
        HearingRequest request = hearing("IDEM-1", f, T, T.plusHours(1));

        SchedulingService.ScheduleOutcome first = service.schedule(request, "key-1");
        SchedulingService.ScheduleOutcome replay = service.schedule(request, "key-1");

        assertThat(first.replay()).isFalse();
        assertThat(replay.replay()).isTrue();
        assertThat(replay.hearing().id()).isEqualTo(first.hearing().id());
        assertThat(hearingRepository.count()).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyDifferentContentConflicts() {
        Fixture f = setup();
        service.schedule(hearing("IDEM-2", f, T, T.plusHours(1)), "key-2");

        HearingRequest changed = hearing("IDEM-2", f, T.plusHours(2), T.plusHours(3));
        assertThatThrownBy(() -> service.schedule(changed, "key-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    // ---------------- 原子改期 ----------------

    @Test
    void successfulRescheduleSwitchesTimeAndIncrementsVersion() {
        Fixture f = setup();
        HearingResponse h = service.schedule(hearing("RS-1", f, T, T.plusHours(1)), null).hearing();

        HearingResponse moved = service.reschedule("RS-1",
                T.plusHours(3), T.plusHours(4), h.version());

        assertThat(moved.startAt()).isEqualTo(T.plusHours(3));
        assertThat(moved.version()).isEqualTo(h.version() + 1);
        // 库里只有一条，原时间已不存在。
        assertThat(hearingRepository.findByCaseNo("RS-1")).isPresent();
        assertThat(hearingRepository.findByCaseNo("RS-1").orElseThrow().getStartAt())
                .isEqualTo(T.plusHours(3));
    }

    @Test
    void failedRescheduleKeepsOriginalTime() {
        Fixture f = setup();
        HearingResponse first = service.schedule(hearing("RS-2", f, T, T.plusHours(1)), null).hearing();
        // 占位新档期目标时段。
        Fixture g = setup();
        service.schedule(hearing("RS-2-OCC", g, T.plusHours(3), T.plusHours(4)), null);

        assertThatThrownBy(() -> service.reschedule("RS-2",
                T.plusHours(3), T.plusHours(4), first.version()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_CONFLICT);

        HearingResponse after = service.getByCaseNo("RS-2");
        assertThat(after.startAt()).isEqualTo(T);
        assertThat(after.endAt()).isEqualTo(T.plusHours(1));
        assertThat(after.version()).isEqualTo(first.version());
    }

    @Test
    void repeatedRescheduleDoesNotCreateDuplicateOccupancy() {
        Fixture f = setup();
        HearingResponse h = service.schedule(hearing("RS-3", f, T, T.plusHours(1)), null).hearing();

        HearingResponse v1 = service.reschedule("RS-3", T.plusHours(2), T.plusHours(3), h.version());
        // 用最新版本再次改到另一个空闲时段，不应与自身冲突。
        HearingResponse v2 = service.reschedule("RS-3", T.plusHours(5), T.plusHours(6), v1.version());

        assertThat(v2.version()).isEqualTo(0L + 2);
        assertThat(hearingRepository.count()).isEqualTo(1);
        assertThat(hearingRepository.findByCaseNo("RS-3").orElseThrow().getStartAt())
                .isEqualTo(T.plusHours(5));
    }

    @Test
    void staleVersionRejected() {
        Fixture f = setup();
        HearingResponse h = service.schedule(hearing("RS-4", f, T, T.plusHours(1)), null).hearing();
        // 先成功改期一次。
        service.reschedule("RS-4", T.plusHours(2), T.plusHours(3), h.version());

        // 再用过期的旧版本改期 -> 版本冲突，且时间保持为已生效的新时间。
        assertThatThrownBy(() -> service.reschedule("RS-4",
                T.plusHours(6), T.plusHours(7), h.version()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERSION_CONFLICT);

        assertThat(service.getByCaseNo("RS-4").startAt()).isEqualTo(T.plusHours(2));
    }

    private record Fixture(JudgeResponse j, CourtroomResponse r, ParticipantResponse p) {
    }
}
