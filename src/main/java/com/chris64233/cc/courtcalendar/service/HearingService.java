package com.chris64233.cc.courtcalendar.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.courtcalendar.api.HearingResponse;
import com.chris64233.cc.courtcalendar.api.ParticipantRef;
import com.chris64233.cc.courtcalendar.api.RescheduleRequest;
import com.chris64233.cc.courtcalendar.api.ScheduleHearingRequest;
import com.chris64233.cc.courtcalendar.domain.Courtroom;
import com.chris64233.cc.courtcalendar.domain.Hearing;
import com.chris64233.cc.courtcalendar.domain.IdempotencyRecord;
import com.chris64233.cc.courtcalendar.domain.Judge;
import com.chris64233.cc.courtcalendar.domain.Participant;
import com.chris64233.cc.courtcalendar.domain.ScheduleMutex;
import com.chris64233.cc.courtcalendar.domain.TimeRange;
import com.chris64233.cc.courtcalendar.exception.BadRequestException;
import com.chris64233.cc.courtcalendar.exception.CaseNumberConflictException;
import com.chris64233.cc.courtcalendar.exception.IdempotencyConflictException;
import com.chris64233.cc.courtcalendar.exception.ResourceNotFoundException;
import com.chris64233.cc.courtcalendar.exception.ResourceConflict;
import com.chris64233.cc.courtcalendar.exception.SchedulingConflictException;
import com.chris64233.cc.courtcalendar.exception.StaleVersionException;
import com.chris64233.cc.courtcalendar.repository.HearingRepository;
import com.chris64233.cc.courtcalendar.repository.IdempotencyRecordRepository;
import com.chris64233.cc.courtcalendar.repository.ScheduleMutexRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 庭审排期核心服务。
 *
 * <p>并发安全：创建与改期事务先通过 {@code SELECT ... FOR UPDATE} 锁定全局唯一的
 * {@link ScheduleMutex} 行，使所有写事务在数据库层面串行执行，杜绝“检查—写入”窗口内的
 * 双重占用。资源时间冲突同时由 {@link ConflictValidator} 在锁内做完整校验。</p>
 *
 * <p>原子改期：原 {@link Hearing} 行保留，仅在新时间通过全部校验后才更新时间字段；
 * 任何冲突都会抛出异常回滚事务，原档期不变。{@code @Version} 乐观锁拒绝基于过期状态的更新。</p>
 */
@Service
public class HearingService {

    private static final Logger log = LoggerFactory.getLogger(HearingService.class);

    private final HearingRepository hearingRepository;
    private final ScheduleMutexRepository mutexRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ResourceService resourceService;
    private final ConflictValidator conflictValidator;
    private final ObjectMapper objectMapper;

    public HearingService(HearingRepository hearingRepository,
                          ScheduleMutexRepository mutexRepository,
                          IdempotencyRecordRepository idempotencyRepository,
                          ResourceService resourceService,
                          ConflictValidator conflictValidator,
                          ObjectMapper objectMapper) {
        this.hearingRepository = hearingRepository;
        this.mutexRepository = mutexRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.resourceService = resourceService;
        this.conflictValidator = conflictValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * 原子排期。
     */
    @Transactional
    public HearingResponse schedule(ScheduleHearingRequest request, String idempotencyKey,
                                    String requestHash) {
        // 锁定全局排期互斥行，写事务串行化
        lockMutex();

        Optional<HearingResponse> replay = checkIdempotency(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        TimeRange target = newRange(request.start(), request.end());

        if (hearingRepository.existsByCaseNumber(request.caseNumber())) {
            throw new CaseNumberConflictException(request.caseNumber());
        }

        Judge judge = resourceService.requireJudgeEntity(request.judgeId());
        Courtroom courtroom = resourceService.requireCourtroomEntity(request.courtroomId());
        List<Participant> participants =
                resourceService.requireParticipantEntities(request.participantIds());
        Set<String> requiredFacilities = FacilitySet.normalize(request.requiredFacilities());

        List<ResourceConflict> conflicts = conflictValidator.validate(
                target, judge, courtroom, participants,
                request.expectedAttendees(), requiredFacilities, null);
        if (!conflicts.isEmpty()) {
            throw new SchedulingConflictException(conflicts);
        }

        Hearing hearing = new Hearing(request.caseNumber(), request.expectedAttendees(),
                requiredFacilities, judge, courtroom, target);
        participants.forEach(hearing::addParticipant);
        hearingRepository.save(hearing);
        // 立即 flush：让唯一约束等冲突在本事务内抛出，并使生成字段（版本等）就位
        hearingRepository.flush();

        HearingResponse response = toResponse(hearing);
        saveIdempotency(idempotencyKey, requestHash, response, hearing.getId());
        log.debug("案件 {} 排期成功: {}", hearing.getCaseNumber(), target);
        return response;
    }

    /**
     * 原子改期：保留原排期校验新时间，全部满足才一次性切换；失败则原档期保留。
     */
    @Transactional
    public HearingResponse reschedule(String caseNumber, RescheduleRequest request,
                                      String idempotencyKey, String requestHash) {
        lockMutex();

        Optional<HearingResponse> replay = checkIdempotency(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Hearing hearing = hearingRepository.findByCaseNumber(caseNumber)
                .orElseThrow(() -> new ResourceNotFoundException("案件", caseNumber));

        // 版本校验：拒绝基于过期状态的更新
        if (hearing.getVersion() != request.version()) {
            throw new StaleVersionException(request.version(), hearing.getVersion());
        }

        TimeRange target = newRange(request.start(), request.end());

        // 原排期保留的情况下校验新档期，排除自身避免与自己冲突；
        // 法官/法庭/参与人沿用原排期，重复改期不会产生重复占用。
        List<Participant> participants = hearing.getParticipants().stream()
                .map(hp -> hp.getParticipant())
                .sorted(Comparator.comparing(Participant::getId))
                .toList();
        List<ResourceConflict> conflicts = conflictValidator.validate(
                target, hearing.getJudge(), hearing.getCourtroom(), participants,
                hearing.getExpectedAttendees(), hearing.getRequiredFacilities(),
                hearing.getId());
        if (!conflicts.isEmpty()) {
            throw new SchedulingConflictException(conflicts);
        }

        // 只有新档期完整成立才一次性切换（同一行 UPDATE，version 由 JPA 自动递增）
        hearing.reschedule(target);
        hearingRepository.save(hearing);
        // flush 使乐观锁版本递增落到实体与数据库，随后读取的响应才是新版本
        hearingRepository.flush();

        HearingResponse response = toResponse(hearing);
        saveIdempotency(idempotencyKey, requestHash, response, hearing.getId());
        log.debug("案件 {} 改期成功: {}", caseNumber, target);
        return response;
    }

    @Transactional(readOnly = true)
    public HearingResponse getByCaseNumber(String caseNumber) {
        return toResponse(hearingRepository.findByCaseNumber(caseNumber)
                .orElseThrow(() -> new ResourceNotFoundException("案件", caseNumber)));
    }

    /**
     * 按资源查询日程，结果按开始时间、案件号稳定排序。
     */
    @Transactional(readOnly = true)
    public List<HearingResponse> getSchedule(String resourceType, Long resourceId) {
        List<Hearing> hearings = switch (resourceType) {
            case "judge" -> {
                resourceService.requireJudgeEntity(resourceId);
                yield hearingRepository.findScheduleForJudge(resourceId);
            }
            case "courtroom" -> {
                resourceService.requireCourtroomEntity(resourceId);
                yield hearingRepository.findScheduleForCourtroom(resourceId);
            }
            case "participant" -> {
                resourceService.requireParticipantEntities(List.of(resourceId));
                yield hearingRepository.findScheduleForParticipant(resourceId);
            }
            default -> throw new BadRequestException(
                    "不支持的资源类型: " + resourceType
                            + "（可选: judge / courtroom / participant）");
        };
        return hearings.stream().map(this::toResponse).toList();
    }

    /* ---------------- 内部辅助 ---------------- */

    private void lockMutex() {
        ScheduleMutex mutex = mutexRepository.lockById(ScheduleMutex.SINGLETON_ID);
        if (mutex == null) {
            // 兜底：初始化器尚未执行时创建唯一互斥行
            mutexRepository.save(new ScheduleMutex(ScheduleMutex.SINGLETON_ID));
            mutexRepository.lockById(ScheduleMutex.SINGLETON_ID);
        }
    }

    /**
     * 幂等检查：键存在且内容相同返回首次结果；键存在但内容不同返回冲突。
     * 调用方已持有互斥锁，并发相同键也会被串行化，不会双重写入。
     */
    private Optional<HearingResponse> checkIdempotency(String key, String requestHash) {
        if (key == null) {
            return Optional.empty();
        }
        return idempotencyRepository.findById(key).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(key);
            }
            return deserialize(record.getResponseBody());
        });
    }

    private void saveIdempotency(String key, String requestHash, HearingResponse response,
                                 Long hearingId) {
        if (key != null) {
            idempotencyRepository.save(new IdempotencyRecord(
                    key, requestHash, serialize(response), 200, hearingId,
                    LocalDateTime.now()));
        }
    }

    private String serialize(HearingResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException e) {
            throw new IllegalStateException("排期结果序列化失败", e);
        }
    }

    private HearingResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, HearingResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("幂等结果反序列化失败", e);
        }
    }

    private static TimeRange newRange(LocalDateTime start, LocalDateTime end) {
        try {
            return new TimeRange(start, end);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private HearingResponse toResponse(Hearing hearing) {
        List<ParticipantRef> participants = hearing.getParticipants().stream()
                .map(hp -> new ParticipantRef(hp.getParticipant().getId(),
                        hp.getParticipant().getName()))
                .toList();
        return new HearingResponse(
                hearing.getId(),
                hearing.getCaseNumber(),
                hearing.getExpectedAttendees(),
                FacilitySet.normalize(hearing.getRequiredFacilities()),
                hearing.getJudge().getId(),
                hearing.getJudge().getName(),
                hearing.getCourtroom().getId(),
                hearing.getCourtroom().getName(),
                participants,
                hearing.getStart(),
                hearing.getEnd(),
                hearing.getVersion());
    }
}
