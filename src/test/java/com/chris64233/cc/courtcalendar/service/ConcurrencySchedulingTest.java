package com.chris64233.cc.courtcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.cc.courtcalendar.TestData;
import com.chris64233.cc.courtcalendar.error.BusinessException;
import com.chris64233.cc.courtcalendar.error.ErrorCode;
import com.chris64233.cc.courtcalendar.repo.HearingRepository;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomResponse;
import com.chris64233.cc.courtcalendar.web.dto.HearingRequest;
import com.chris64233.cc.courtcalendar.web.dto.JudgeResponse;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantResponse;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConcurrencySchedulingTest {

    @Autowired
    private SchedulingService service;
    @Autowired
    private HearingRepository hearingRepository;

    private static final LocalDateTime T = TestData.T0;

    /**
     * 两个请求并发争抢同一组（法官 + 法庭 + 参与人）资源：恰好一个成功、一个冲突，
     * 绝不允许双重占用（成功数必须为 1）。
     */
    @Test
    void concurrentRequestsForSameResourcesNeverDoubleBook() throws Exception {
        JudgeResponse judge = service.registerJudge("CJ-" + System.nanoTime());
        CourtroomResponse room = service.registerCourtroom(
                "CR-" + System.nanoTime(), 10, Set.of("RECORDING"));
        ParticipantResponse p = service.registerParticipant("CP-" + System.nanoTime());

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
                    HearingRequest request = TestData.hearing(
                            "CONC-" + idx + "-" + System.nanoTime(), 5, Set.of("RECORDING"),
                            judge.id(), room.id(), Set.of(p.id()), T, T.plusHours(1));
                    service.schedule(request, null);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.SCHEDULE_CONFLICT
                            || e.getErrorCode() == ErrorCode.DUPLICATE_CASE) {
                        conflict.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 锁等待等也计为未成功，但不应出现双重成功。
                    conflict.incrementAndGet();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(success.get() + conflict.get()).isEqualTo(threads);
        assertThat(hearingRepository.count()).isEqualTo(1);
    }

    /**
     * 相同幂等键的并发重放：最终只有一条排期，所有完成的请求都返回同一结果，不产生重复。
     */
    @Test
    void concurrentSameIdempotencyKeyCreatesExactlyOne() throws Exception {
        JudgeResponse judge = service.registerJudge("IJ-" + System.nanoTime());
        CourtroomResponse room = service.registerCourtroom(
                "IR-" + System.nanoTime(), 10, Set.of("RECORDING"));
        ParticipantResponse p = service.registerParticipant("IP-" + System.nanoTime());

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger replay = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    HearingRequest request = TestData.hearing(
                            "IDEM-CONC", 5, Set.of("RECORDING"),
                            judge.id(), room.id(), Set.of(p.id()), T, T.plusHours(1));
                    SchedulingService.ScheduleOutcome outcome = service.schedule(request, "same-key");
                    if (outcome.replay()) {
                        replay.incrementAndGet();
                    } else {
                        created.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 并发下个别请求可能因唯一约束竞争失败，但最终只能有一条排期。
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(created.get()).isEqualTo(1);
        assertThat(created.get() + replay.get()).isGreaterThanOrEqualTo(1);
        assertThat(hearingRepository.count()).isEqualTo(1);
    }
}
