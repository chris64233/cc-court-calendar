package com.chris64233.cc.courtcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.cc.courtcalendar.TestData;
import com.chris64233.cc.courtcalendar.domain.ResourceType;
import com.chris64233.cc.courtcalendar.error.BusinessException;
import com.chris64233.cc.courtcalendar.error.ConflictDetail;
import com.chris64233.cc.courtcalendar.error.ConflictReason;
import com.chris64233.cc.courtcalendar.error.ErrorCode;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomResponse;
import com.chris64233.cc.courtcalendar.web.dto.HearingRequest;
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
class SchedulingServiceRulesTest {

    @Autowired
    private SchedulingService service;

    private static final LocalDateTime T = TestData.T0;

    private Fixture setup(int capacity, String... courtroomFacilities) {
        JudgeResponse judge = service.registerJudge("J-" + System.nanoTime());
        CourtroomResponse room = service.registerCourtroom(
                "R-" + System.nanoTime(), capacity, Set.of(courtroomFacilities));
        ParticipantResponse p1 = service.registerParticipant("P1-" + System.nanoTime());
        ParticipantResponse p2 = service.registerParticipant("P2-" + System.nanoTime());
        return new Fixture(judge, room, p1, p2);
    }

    private HearingRequest req(String caseNo, Fixture f, LocalDateTime s, LocalDateTime e) {
        return req(caseNo, f, 5, Set.of("RECORDING"), s, e);
    }

    private HearingRequest req(String caseNo, Fixture f, int people, Set<String> facilities,
                               LocalDateTime s, LocalDateTime e) {
        return TestData.hearing(caseNo, people, facilities, f.judge().id(), f.room().id(),
                Set.of(f.p1().id(), f.p2().id()), s, e);
    }

    @Test
    void successfulScheduleOccupiesAllResources() {
        Fixture f = setup(10, "RECORDING", "PROJECTOR");
        HearingResponse h = service.schedule(req("CASE-1", f, T, T.plusHours(1)), null).hearing();
        assertThat(h.caseNo()).isEqualTo("CASE-1");
        assertThat(h.participants()).hasSize(2);
        assertThat(h.requiredFacilities()).containsExactly("RECORDING");
        assertThat(h.version()).isZero();
    }

    @Test
    void duplicateCaseNoRejected() {
        Fixture f = setup(10, "RECORDING");
        service.schedule(req("DUP", f, T, T.plusHours(1)), null);
        Fixture g = setup(10, "RECORDING");
        assertThatThrownBy(() -> service.schedule(req("DUP", g, T, T.plusHours(1)), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_CASE);
    }

    @Test
    void halfOpenInterval_adjacentDoesNotConflict_butOverlapDoes() {
        Fixture f = setup(10, "RECORDING");
        service.schedule(req("ADJ-1", f, T, T.plusHours(1)), null);

        // 首尾相接（左闭右开）：[09:00,10:00) 与 [10:00,11:00) 不冲突。
        HearingResponse adjacent = service.schedule(
                req("ADJ-2", f, T.plusHours(1), T.plusHours(2)), null).hearing();
        assertThat(adjacent.caseNo()).isEqualTo("ADJ-2");

        // 任何真正重叠都冲突。
        assertThatThrownBy(() -> service.schedule(
                req("ADJ-3", f, T.plusMinutes(30), T.plusMinutes(90)), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void judgeBufferMustBeRespectedBetweenAdjacentHearings() {
        Fixture f = setup(10, "RECORDING");
        service.schedule(req("BUF-1", f, T, T.plusHours(1)), null);

        // 间隔正好 15 分钟：缓冲满足（上一场 end=10:00，下一场 start=10:15，间隔=15m）。
        HearingResponse ok = service.schedule(
                req("BUF-2", f, T.plusHours(1).plusMinutes(15), T.plusHours(2).plusMinutes(15)),
                null).hearing();
        assertThat(ok.caseNo()).isEqualTo("BUF-2");

        // 间隔仅 10 分钟：缓冲不足，返回 BUFFER 冲突，且定位到法官。
        BusinessException ex = catchConflict(() -> service.schedule(
                req("BUF-3", f, T.plusHours(2).plusMinutes(25), T.plusHours(3)), null));
        assertThat(ex.getConflicts())
                .anyMatch(c -> "JUDGE".equals(c.resourceType())
                        && ConflictReason.BUFFER.equals(c.reason())
                        && "BUF-2".equals(c.conflictingCaseNo()));
    }

    @Test
    void insufficientCapacityReportedOnCourtroom() {
        Fixture f = setup(5, "RECORDING");
        BusinessException ex = catchConflict(
                () -> service.schedule(req("CAP", f, 20, Set.of("RECORDING"), T, T.plusHours(1)),
                        null));
        assertThat(ex.getConflicts()).anyMatch(c ->
                "COURTROOM".equals(c.resourceType()) && ConflictReason.CAPACITY.equals(c.reason()));
    }

    @Test
    void missingFacilityReportedWithMissingList() {
        Fixture f = setup(50, "RECORDING");
        BusinessException ex = catchConflict(() -> service.schedule(
                req("FAC", f, 5, Set.of("RECORDING", "INTERPRETER"), T, T.plusHours(1)), null));
        ConflictDetail detail = ex.getConflicts().stream()
                .filter(c -> ConflictReason.MISSING_FACILITY.equals(c.reason()))
                .findFirst().orElseThrow();
        assertThat(detail.missingFacilities()).containsExactly("INTERPRETER");
    }

    @Test
    void unavailableWindowBlocksJudgeCourtroomAndParticipant() {
        Fixture f = setup(10, "RECORDING");
        service.addUnavailableWindow(ResourceType.JUDGE, f.judge().id(),
                T, T.plusHours(1), "休假");
        service.addUnavailableWindow(ResourceType.COURTROOM, f.room().id(),
                T, T.plusHours(1), null);
        service.addUnavailableWindow(ResourceType.PARTICIPANT, f.p1().id(),
                T, T.plusHours(1), null);

        BusinessException ex = catchConflict(
                () -> service.schedule(req("UNAV", f, T, T.plusMinutes(30)), null));
        List<String> types = ex.getConflicts().stream()
                .filter(c -> ConflictReason.UNAVAILABLE.equals(c.reason()))
                .map(ConflictDetail::resourceType).toList();
        assertThat(types).contains("JUDGE", "COURTROOM", "PARTICIPANT");
    }

    @Test
    void participantDoubleBookingIsDetected() {
        Fixture f = setup(10, "RECORDING");
        // 第二名参与人独立，仅 p1 复用。
        ParticipantResponse other = service.registerParticipant("OTHER-" + System.nanoTime());
        service.schedule(req("PB-1", f, T, T.plusHours(1)), null);

        HearingRequest overlapping = TestData.hearing("PB-2", 5, Set.of("RECORDING"),
                service.registerJudge("J2-" + System.nanoTime()).id(),
                service.registerCourtroom("R2-" + System.nanoTime(), 10, Set.of("RECORDING")).id(),
                Set.of(f.p1().id(), other.id()), T.plusMinutes(30), T.plusMinutes(90));
        BusinessException ex = catchConflict(() -> service.schedule(overlapping, null));
        assertThat(ex.getConflicts()).anyMatch(c ->
                "PARTICIPANT".equals(c.resourceType())
                        && f.p1().id().equals(c.resourceId())
                        && "PB-1".equals(c.conflictingCaseNo()));
    }

    @Test
    void endBeforeStartIsValidationError() {
        Fixture f = setup(10, "RECORDING");
        assertThatThrownBy(() -> service.schedule(req("BAD", f, T.plusHours(1), T), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException catchConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        try {
            call.call();
            throw new AssertionError("期望抛出 BusinessException");
        } catch (BusinessException e) {
            return e;
        } catch (Throwable t) {
            throw new AssertionError("期望 BusinessException，实际: " + t, t);
        }
    }

    private record Fixture(JudgeResponse judge, CourtroomResponse room,
                           ParticipantResponse p1, ParticipantResponse p2) {
    }
}
