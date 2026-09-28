package com.chris64233.cc.courtcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.cc.courtcalendar.api.HearingResponse;
import com.chris64233.cc.courtcalendar.api.RescheduleRequest;
import com.chris64233.cc.courtcalendar.api.ScheduleHearingRequest;
import com.chris64233.cc.courtcalendar.exception.ConflictReason;
import com.chris64233.cc.courtcalendar.exception.SchedulingConflictException;
import com.chris64233.cc.courtcalendar.exception.StaleVersionException;
import com.chris64233.cc.courtcalendar.service.HearingService;

/**
 * 排期核心业务规则的集成测试。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:db-scheduling;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class HearingSchedulingIntegrationTests {

    @Autowired
    private HearingService hearingService;
    @Autowired
    private TestFixtures fixtures;

    private ScheduleHearingRequest morning(String caseNumber, Long judge, Long room,
                                           List<Long> participants, int attendees,
                                           Set<String> facilities) {
        return fixtures.hearing(caseNumber, attendees, facilities, judge, room,
                participants, "2026-10-01T09:00:00", "2026-10-01T10:00:00");
    }

    @Test
    void schedulesWhenAllResourcesFree() {
        Long judge = fixtures.judge("法官A");
        Long room = fixtures.courtroom("第一法庭", 20, "录音", "显示屏");
        Long p1 = fixtures.participant("书记员");
        Long p2 = fixtures.participant("律师");

        HearingResponse response = hearingService.schedule(
                morning("CASE-001", judge, room, List.of(p1, p2), 10,
                        new LinkedHashSet<>(Set.of("录音"))), null, "hash-1");

        assertThat(response.id()).isNotNull();
        assertThat(response.caseNumber()).isEqualTo("CASE-001");
        assertThat(response.version()).isZero();
        assertThat(response.participants()).hasSize(2);
        assertThat(response.start()).isEqualTo("2026-10-01T09:00");
    }

    @Test
    void rejectsOverlappingJudgeAndParticipantAndCourtroom() {
        Long judge = fixtures.judge("法官J");
        Long room = fixtures.courtroom("庭R", 30, "标准");
        Long p1 = fixtures.participant("参与人P1");

        hearingService.schedule(morning("CASE-A", judge, room, List.of(p1), 5,
                Set.of("标准")), null, "h-a");

        // 同一法官、同一法庭、同一参与人，时间重叠
        ScheduleHearingRequest clash = morning("CASE-B", judge, room, List.of(p1), 5,
                Set.of("标准"));
        assertThatThrownBy(() -> hearingService.schedule(clash, null, "h-b"))
                .isInstanceOf(SchedulingConflictException.class)
                .satisfies(ex -> {
                    SchedulingConflictException sc = (SchedulingConflictException) ex;
                    assertThat(sc.getConflicts()).extracting(c -> c.resourceType())
                            .contains("judge", "courtroom", "participant");
                    assertThat(sc.getConflicts())
                            .allMatch(c -> c.reason() == ConflictReason.OCCUPIED);
                    assertThat(sc.getConflicts())
                            .anyMatch(c -> "CASE-A".equals(c.conflictingCase()));
                });
    }

    @Test
    void adjacentIntervalsDoNotConflictButBufferDoes() {
        Long judge = fixtures.judge("法官缓冲");
        Long room1 = fixtures.courtroom("缓冲庭1", 10, "标准");
        Long room2 = fixtures.courtroom("缓冲庭2", 10, "标准");
        Long otherParticipant = fixtures.participant("其他参与人");

        // 第一场：法官 J，法庭 room1，参与人 otherParticipant，09:00-10:00
        hearingService.schedule(morning("CASE-BUF-1", judge, room1,
                List.of(otherParticipant), 3, Set.of("标准")), null, "buf1");

        // 第二场：同一法官，另一法庭、另一参与人，10:00-11:00 → 间隔 0 违反 15 分钟缓冲
        ScheduleHearingRequest tooClose = new ScheduleHearingRequest("CASE-BUF-2", 3,
                Set.of("标准"), judge, room2, List.of(fixtures.participant("第二场参与人")),
                java.time.LocalDateTime.parse("2026-10-01T10:00:00"),
                java.time.LocalDateTime.parse("2026-10-01T11:00:00"));

        assertThatThrownBy(() -> hearingService.schedule(tooClose, null, "buf2"))
                .isInstanceOf(SchedulingConflictException.class)
                .satisfies(ex -> {
                    SchedulingConflictException sc = (SchedulingConflictException) ex;
                    // 只有法官缓冲冲突：法庭和参与人都换了，不占用
                    assertThat(sc.getConflicts())
                            .singleElement()
                            .satisfies(c -> {
                                assertThat(c.resourceType()).isEqualTo("judge");
                                assertThat(c.reason()).isEqualTo(ConflictReason.BUFFER);
                                assertThat(c.conflictingCase()).isEqualTo("CASE-BUF-1");
                            });
                });

        // 间隔恰好 15 分钟（10:15 开始，左闭右开）→ 满足缓冲，排期成功
        ScheduleHearingRequest okay = new ScheduleHearingRequest("CASE-BUF-3", 3,
                Set.of("标准"), judge, room2, List.of(fixtures.participant("第三场参与人")),
                java.time.LocalDateTime.parse("2026-10-01T10:15:00"),
                java.time.LocalDateTime.parse("2026-10-01T11:15:00"));
        HearingResponse response = hearingService.schedule(okay, null, "buf3");
        assertThat(response.caseNumber()).isEqualTo("CASE-BUF-3");
    }

    @Test
    void rejectsUnavailableRanges() {
        Long judge = fixtures.judge("法官休庭", List.of(TestFixtures.range(
                "2026-10-01T08:00:00", "2026-10-01T12:00:00")));
        Long room = fixtures.courtroom("封闭法庭", 10, List.of(TestFixtures.range(
                "2026-10-01T09:30:00", "2026-10-01T10:30:00")), "标准");
        Long p = fixtures.participant("请假参与人", List.of(TestFixtures.range(
                "2026-10-01T00:00:00", "2026-10-02T00:00:00")));

        ScheduleHearingRequest request = morning("CASE-UNAV", judge, room, List.of(p), 3,
                Set.of("标准"));
        assertThatThrownBy(() -> hearingService.schedule(request, null, "unav"))
                .isInstanceOf(SchedulingConflictException.class)
                .satisfies(ex -> {
                    SchedulingConflictException sc = (SchedulingConflictException) ex;
                    assertThat(sc.getConflicts())
                            .extracting(c -> c.resourceType() + ":" + c.reason())
                            .contains("judge:UNAVAILABLE",
                                    "courtroom:UNAVAILABLE",
                                    "participant:UNAVAILABLE");
                });
    }

    @Test
    void rejectsCapacityAndFacilityShortfalls() {
        Long judge = fixtures.judge("法官Cap");
        Long room = fixtures.courtroom("小庭", 5, "桌椅");
        Long p = fixtures.participant("参与人Cap");

        ScheduleHearingRequest request = morning("CASE-CAP", judge, room, List.of(p), 20,
                new LinkedHashSet<>(Set.of("桌椅", "同声传译")));

        assertThatThrownBy(() -> hearingService.schedule(request, null, "cap"))
                .isInstanceOf(SchedulingConflictException.class)
                .satisfies(ex -> {
                    SchedulingConflictException sc = (SchedulingConflictException) ex;
                    assertThat(sc.getConflicts())
                            .anyMatch(c -> c.reason() == ConflictReason.CAPACITY);
                    assertThat(sc.getConflicts())
                            .anyMatch(c -> c.reason() == ConflictReason.FACILITY
                                    && c.detail().contains("同声传译"));
                });
    }

    @Test
    void atomicRescheduleKeepsOriginalSlotOnFailureAndSwitchesOnSuccess() {
        Long judge = fixtures.judge("法官改期");
        Long room = fixtures.courtroom("改期庭", 10, "标准");
        Long p = fixtures.participant("改期参与人");

        HearingResponse original = hearingService.schedule(
                morning("CASE-MOVE", judge, room, List.of(p), 3, Set.of("标准")), null, "m1");
        assertThat(original.version()).isZero();

        // 同一法官次日 15:00 已有另一场庭审
        hearingService.schedule(fixtures.hearing("CASE-JUDGE-BUSY", 3, Set.of("标准"), judge,
                fixtures.courtroom("改期庭2", 10, "标准"),
                List.of(fixtures.participant("其他参与人")),
                "2026-10-02T15:00:00", "2026-10-02T16:00:00"), null, "m3");

        // 改期到 10-02 15:00 → 法官冲突，失败；原档期保留且版本不变
        RescheduleRequest badMove = new RescheduleRequest(0L,
                java.time.LocalDateTime.parse("2026-10-02T15:00:00"),
                java.time.LocalDateTime.parse("2026-10-02T16:00:00"));
        assertThatThrownBy(() -> hearingService.reschedule("CASE-MOVE", badMove, null, "m4"))
                .isInstanceOf(SchedulingConflictException.class)
                .satisfies(ex -> assertThat(((SchedulingConflictException) ex).getConflicts())
                        .anyMatch(c -> c.reason() == ConflictReason.OCCUPIED
                                && "CASE-JUDGE-BUSY".equals(c.conflictingCase())));

        HearingResponse unchanged = hearingService.getByCaseNumber("CASE-MOVE");
        assertThat(unchanged.start()).isEqualTo("2026-10-01T09:00");
        assertThat(unchanged.version()).isZero();

        // 改到当日 11:00-12:00（与原庭间隔 60 分钟，满足缓冲）→ 成功，版本 +1
        RescheduleRequest goodMove = new RescheduleRequest(0L,
                java.time.LocalDateTime.parse("2026-10-01T11:00:00"),
                java.time.LocalDateTime.parse("2026-10-01T12:00:00"));
        HearingResponse moved = hearingService.reschedule("CASE-MOVE", goodMove, null, "m5");
        assertThat(moved.start()).isEqualTo("2026-10-01T11:00");
        assertThat(moved.version()).isEqualTo(1);

        // 原时间段已释放，且与 11:00 新档期间隔 60 分钟：原资源可排新庭
        HearingResponse replacement = hearingService.schedule(
                morning("CASE-REPLACEMENT", judge, room, List.of(p), 3, Set.of("标准")),
                null, "m6");
        assertThat(replacement.caseNumber()).isEqualTo("CASE-REPLACEMENT");

        // 用过期版本 0 再改 → 拒绝
        RescheduleRequest stale = new RescheduleRequest(0L,
                java.time.LocalDateTime.parse("2026-10-03T09:00:00"),
                java.time.LocalDateTime.parse("2026-10-03T10:00:00"));
        assertThatThrownBy(() -> hearingService.reschedule("CASE-MOVE", stale, null, "m7"))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void scheduleIsStableSortedWhenQueryingByResource() {
        Long judge = fixtures.judge("排序法官");
        Long room = fixtures.courtroom("排序庭", 10, "标准");
        Long p = fixtures.participant("排序参与人");

        hearingService.schedule(fixtures.hearing("CASE-LATE", 3, Set.of("标准"), judge, room,
                List.of(p), "2026-11-01T14:00:00", "2026-11-01T15:00:00"), null, "s1");
        hearingService.schedule(fixtures.hearing("CASE-EARLY", 3, Set.of("标准"), judge, room,
                List.of(p), "2026-11-01T09:00:00", "2026-11-01T10:00:00"), null, "s2");

        List<HearingResponse> judgeSchedule = hearingService.getSchedule("judge", judge);
        assertThat(judgeSchedule).extracting(HearingResponse::caseNumber)
                .containsExactly("CASE-EARLY", "CASE-LATE");

        List<HearingResponse> participantSchedule =
                hearingService.getSchedule("participant", p);
        assertThat(participantSchedule).hasSize(2)
                .extracting(HearingResponse::caseNumber)
                .containsExactly("CASE-EARLY", "CASE-LATE");

        List<HearingResponse> roomSchedule = hearingService.getSchedule("courtroom", room);
        assertThat(roomSchedule).hasSize(2);
    }

    @Test
    void concurrentRequestsForSameResourceNeverDoubleBook() throws Exception {
        Long judge = fixtures.judge("并发法官");
        Long room = fixtures.courtroom("并发庭", 10, "标准");
        Long p = fixtures.participant("并发参与人");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    ScheduleHearingRequest request = morning("CASE-CONC-" + idx, judge, room,
                            List.of(p), 3, Set.of("标准"));
                    hearingService.schedule(request, null, "conc-" + idx);
                    success.incrementAndGet();
                } catch (SchedulingConflictException e) {
                    conflict.incrementAndGet();
                } catch (Exception e) {
                    // 锁等待等异常也记录，但不应出现双重占用
                    conflict.incrementAndGet();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(threads - 1);
        // 数据中该时段只有一场庭审
        assertThat(hearingService.getSchedule("judge", judge)).hasSize(1);
        assertThat(hearingService.getSchedule("courtroom", room)).hasSize(1);
        assertThat(hearingService.getSchedule("participant", p)).hasSize(1);
    }

    @Test
    void concurrentReschedulesWithSameVersionApplyExactlyOnce() throws Exception {
        Long judge = fixtures.judge("并发改期法官");
        Long room = fixtures.courtroom("并发改期庭", 10, "标准");
        Long p = fixtures.participant("并发改期参与人");

        hearingService.schedule(morning("CASE-CONC-MOVE", judge, room, List.of(p), 3,
                Set.of("标准")), null, "cm0");

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger stale = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int day = 20 + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    // 两个请求都基于过期后相同的版本 0，改到不同日期
                    RescheduleRequest move = new RescheduleRequest(0L,
                            java.time.LocalDateTime.parse("2026-12-" + day + "T09:00:00"),
                            java.time.LocalDateTime.parse("2026-12-" + day + "T10:00:00"));
                    hearingService.reschedule("CASE-CONC-MOVE", move, null, "cm-" + day);
                    success.incrementAndGet();
                } catch (StaleVersionException e) {
                    stale.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(stale.get()).isEqualTo(1);

        // 只有一场排期，版本为 1，没有重复占用
        List<HearingResponse> schedule = hearingService.getSchedule("judge", judge);
        assertThat(schedule).singleElement().satisfies(h -> {
            assertThat(h.caseNumber()).isEqualTo("CASE-CONC-MOVE");
            assertThat(h.version()).isEqualTo(1);
        });
    }
}
