package com.chris64233.cc.courtcalendar.service;

import com.chris64233.cc.courtcalendar.domain.Courtroom;
import com.chris64233.cc.courtcalendar.domain.Hearing;
import com.chris64233.cc.courtcalendar.domain.IdempotentRequest;
import com.chris64233.cc.courtcalendar.domain.Judge;
import com.chris64233.cc.courtcalendar.domain.Participant;
import com.chris64233.cc.courtcalendar.domain.ResourceType;
import com.chris64233.cc.courtcalendar.domain.UnavailableWindow;
import com.chris64233.cc.courtcalendar.error.BusinessException;
import com.chris64233.cc.courtcalendar.error.ConflictDetail;
import com.chris64233.cc.courtcalendar.error.ConflictReason;
import com.chris64233.cc.courtcalendar.error.ErrorCode;
import com.chris64233.cc.courtcalendar.repo.CourtroomRepository;
import com.chris64233.cc.courtcalendar.repo.HearingRepository;
import com.chris64233.cc.courtcalendar.repo.IdempotentRequestRepository;
import com.chris64233.cc.courtcalendar.repo.JudgeRepository;
import com.chris64233.cc.courtcalendar.repo.ParticipantRepository;
import com.chris64233.cc.courtcalendar.repo.UnavailableWindowRepository;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomResponse;
import com.chris64233.cc.courtcalendar.web.dto.HearingRequest;
import com.chris64233.cc.courtcalendar.web.dto.HearingResponse;
import com.chris64233.cc.courtcalendar.web.dto.JudgeResponse;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantResponse;
import com.chris64233.cc.courtcalendar.web.dto.UnavailableWindowResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 庭审排期核心服务。
 *
 * <p>并发正确性：排期/改期事务按固定顺序（幂等键 → 法官 → 法庭 → 参与人 id 升序）对资源行
 * 加悲观写锁，因此任意两个争抢同一资源的事务都会串行化，绝不可能形成双重占用；固定顺序避免死锁。
 *
 * <p>原子性：一次排期对全部资源在同一事务内校验并写入，任一校验失败整体回滚。
 * 改期在切换前完整校验新档期，只有新档期完整成立时才一次性修改时间，原档期在失败时原样保留。
 */
@Service
public class SchedulingService {

    private final JudgeRepository judgeRepository;
    private final CourtroomRepository courtroomRepository;
    private final ParticipantRepository participantRepository;
    private final UnavailableWindowRepository unavailableWindowRepository;
    private final HearingRepository hearingRepository;
    private final IdempotentRequestRepository idempotentRequestRepository;
    private final HearingMapper mapper;
    private final Duration judgeBuffer;

    @PersistenceContext
    private EntityManager entityManager;

    public SchedulingService(JudgeRepository judgeRepository,
                             CourtroomRepository courtroomRepository,
                             ParticipantRepository participantRepository,
                             UnavailableWindowRepository unavailableWindowRepository,
                             HearingRepository hearingRepository,
                             IdempotentRequestRepository idempotentRequestRepository,
                             HearingMapper mapper,
                             @Value("${courtcalendar.judge-buffer:PT15M}") Duration judgeBuffer) {
        this.judgeRepository = judgeRepository;
        this.courtroomRepository = courtroomRepository;
        this.participantRepository = participantRepository;
        this.unavailableWindowRepository = unavailableWindowRepository;
        this.hearingRepository = hearingRepository;
        this.idempotentRequestRepository = idempotentRequestRepository;
        this.mapper = mapper;
        this.judgeBuffer = judgeBuffer;
    }

    // ---------------------------------------------------------------------
    // 资源登记
    // ---------------------------------------------------------------------

    @Transactional
    public JudgeResponse registerJudge(String name) {
        if (judgeRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "法官名称已存在: " + name);
        }
        return mapper.toJudgeResponse(judgeRepository.save(new Judge(name)));
    }

    @Transactional
    public CourtroomResponse registerCourtroom(String name, int capacity, Set<String> facilities) {
        if (courtroomRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "法庭名称已存在: " + name);
        }
        Courtroom saved = courtroomRepository.save(
                new Courtroom(name, capacity, SchedulingSupport.normalizeFacilities(facilities)));
        return mapper.toCourtroomResponse(saved);
    }

    @Transactional
    public ParticipantResponse registerParticipant(String name) {
        if (participantRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME, "参与人名称已存在: " + name);
        }
        return mapper.toParticipantResponse(participantRepository.save(new Participant(name)));
    }

    @Transactional
    public UnavailableWindowResponse addUnavailableWindow(ResourceType type, Long resourceId,
                                                          LocalDateTime startAt, LocalDateTime endAt,
                                                          String reason) {
        validateInterval(startAt, endAt);
        ensureResourceExists(type, resourceId);
        UnavailableWindow saved = unavailableWindowRepository.save(
                new UnavailableWindow(type, resourceId, startAt, endAt, reason));
        return new UnavailableWindowResponse(saved.getId(), saved.getResourceType().name(),
                saved.getResourceId(), saved.getStartAt(), saved.getEndAt(), saved.getReason());
    }

    // ---------------------------------------------------------------------
    // 排期
    // ---------------------------------------------------------------------

    /**
     * 原子排期。{@code idempotencyKey} 非空时启用幂等：相同键 + 相同内容返回原结果，内容不同冲突。
     *
     * @return 排期结果（重放时 {@code replay=true}，返回原庭审）
     */
    @Transactional
    public ScheduleOutcome schedule(HearingRequest request, String idempotencyKey) {
        validateInterval(request.startAt(), request.endAt());

        // 案件号唯一（提前给出明确错误，最终仍由数据库唯一约束兜底）。
        if (hearingRepository.existsByCaseNo(request.caseNo())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CASE,
                    "案件号已存在: " + request.caseNo());
        }

        String fingerprint = SchedulingSupport.fingerprint(
                request.caseNo(), request.expectedPeople(), request.requiredFacilities(),
                request.judgeId(), request.courtroomId(), request.participantIds(),
                request.startAt().toString(), request.endAt().toString());

        // 1) 幂等记录已存在：相同内容重放返回原结果，不同内容冲突。
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotentRequest> existing =
                    idempotentRequestRepository.findByIdForUpdate(idempotencyKey.trim());
            if (existing.isPresent()) {
                return handleReplay(existing.get(), fingerprint);
            }
        }

        // 2) 固定顺序锁定全部资源（含参与人 id 升序），串行化并发争抢。
        LockedResources locked = lockResources(request.judgeId(), request.courtroomId(),
                request.participantIds());

        // 3) 锁内复查幂等键，防止两个并发请求同时通过步骤 1 的空判断。
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotentRequest> afterLock =
                    idempotentRequestRepository.findByIdForUpdate(idempotencyKey.trim());
            if (afterLock.isPresent()) {
                return handleReplay(afterLock.get(), fingerprint);
            }
        }

        Set<String> required = SchedulingSupport.normalizeFacilities(request.requiredFacilities());

        // 4) 全量校验，收集全部冲突。
        List<ConflictDetail> conflicts = validate(
                locked.judge(), locked.courtroom(), locked.participants(),
                request.startAt(), request.endAt(), request.expectedPeople(), required, null);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT, "排期冲突，无法占用以下资源", conflicts);
        }

        // 5) 原子写入。
        Hearing hearing = new Hearing(
                request.caseNo(), request.expectedPeople(),
                SchedulingSupport.joinFacilities(required),
                locked.judge(), locked.courtroom(),
                new LinkedHashSet<>(locked.participants()),
                request.startAt(), request.endAt());
        try {
            hearingRepository.saveAndFlush(hearing);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotentRequestRepository.saveAndFlush(
                        new IdempotentRequest(idempotencyKey.trim(), fingerprint, hearing.getId()));
            }
        } catch (DataIntegrityViolationException e) {
            // 并发下案件号唯一约束或幂等键唯一约束兜底。
            throw new BusinessException(ErrorCode.DUPLICATE_CASE, "案件号或幂等键冲突，请重试");
        }
        return new ScheduleOutcome(mapper.toHearingResponse(hearing), false);
    }

    private ScheduleOutcome handleReplay(IdempotentRequest record, String currentFingerprint) {
        if (!record.getRequestFingerprint().equals(currentFingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "相同幂等键对应不同的请求内容");
        }
        Hearing original = hearingRepository.findById(record.getHearingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "原排期记录不存在"));
        return new ScheduleOutcome(mapper.toHearingResponse(original), true);
    }

    // ---------------------------------------------------------------------
    // 原子改期
    // ---------------------------------------------------------------------

    /**
     * 在保留原排期的前提下校验并锁定新时间；只有新档期完整成立时才一次性切换。
     * 重复改期不会产生重复占用（冲突检测排除自身）；版本号不匹配拒绝过期更新。
     */
    @Transactional
    public HearingResponse reschedule(String caseNo, LocalDateTime newStart, LocalDateTime newEnd,
                                      long expectedVersion) {
        validateInterval(newStart, newEnd);

        Hearing hearing = hearingRepository.findByCaseNo(caseNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "案件不存在: " + caseNo));

        // 固定顺序锁定同一组资源。
        Set<Long> participantIds = hearing.getParticipants().stream()
                .map(Participant::getId).collect(Collectors.toSet());
        LockedResources locked = lockResources(hearing.getJudge().getId(),
                hearing.getCourtroom().getId(), participantIds);

        // 锁内从数据库刷新到最新状态，再用最新版本号比对，确保拒绝基于过期状态的更新。
        entityManager.refresh(hearing);
        if (hearing.getVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,
                    "排期已被其他操作更新，请基于最新版本重试");
        }

        // 校验新档期，排除自身原档期，避免把自己算作冲突而生成“重复占用”。
        Set<String> required = SchedulingSupport.normalizeFacilities(
                parseFacilities(hearing.getRequiredFacilities()));
        List<ConflictDetail> conflicts = validate(
                locked.judge(), locked.courtroom(), locked.participants(),
                newStart, newEnd, hearing.getExpectedPeople(), required, hearing.getId());
        if (!conflicts.isEmpty()) {
            // 失败：不修改 hearing，事务回滚，原档期原样保留。
            throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT,
                    "改期冲突，已保留原排期", conflicts);
        }

        hearing.reschedule(newStart, newEnd);
        try {
            hearingRepository.saveAndFlush(hearing);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,
                    "排期已被其他操作更新，请基于最新版本重试");
        }
        return mapper.toHearingResponse(hearing);
    }

    // ---------------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HearingResponse getByCaseNo(String caseNo) {
        Hearing hearing = hearingRepository.findByCaseNo(caseNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "案件不存在: " + caseNo));
        return mapper.toHearingResponse(hearing);
    }

    @Transactional(readOnly = true)
    public List<HearingResponse> getSchedule(ResourceType type, Long resourceId,
                                             LocalDateTime startAt, LocalDateTime endAt) {
        ensureResourceExists(type, resourceId);
        List<Hearing> hearings = switch (type) {
            case JUDGE -> hearingRepository.findJudgeSchedule(resourceId, startAt, endAt);
            case COURTROOM -> hearingRepository.findCourtroomSchedule(resourceId, startAt, endAt);
            case PARTICIPANT -> hearingRepository.findParticipantSchedule(resourceId, startAt, endAt);
        };
        return hearings.stream().map(mapper::toHearingResponse).toList();
    }

    // ---------------------------------------------------------------------
    // 内部：加锁与校验
    // ---------------------------------------------------------------------

    private LockedResources lockResources(Long judgeId, Long courtroomId, Set<Long> participantIds) {
        Judge judge = judgeRepository.findByIdForUpdate(judgeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "法官不存在: id=" + judgeId));
        Courtroom courtroom = courtroomRepository.findByIdForUpdate(courtroomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "法庭不存在: id=" + courtroomId));
        // 参与人按 id 升序加锁，杜绝多参与人事务之间的锁顺序倒置。
        List<Long> sortedIds = participantIds == null ? List.of()
                : participantIds.stream().sorted().toList();
        List<Participant> participants = new ArrayList<>();
        for (Long pid : sortedIds) {
            Participant p = participantRepository.findByIdForUpdate(pid)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "参与人不存在: id=" + pid));
            participants.add(p);
        }
        return new LockedResources(judge, courtroom, participants);
    }

    /**
     * 校验全部资源，收集所有冲突而非快速失败，便于一次性反馈。
     *
     * @param excludeHearingId 改期时为自身庭审 id，创建时为 null
     */
    private List<ConflictDetail> validate(Judge judge, Courtroom courtroom,
                                          List<Participant> participants,
                                          LocalDateTime startAt, LocalDateTime endAt,
                                          int expectedPeople, Set<String> requiredFacilities,
                                          Long excludeHearingId) {
        List<ConflictDetail> conflicts = new ArrayList<>();

        // 法官：缓冲时间扩展后的时间冲突 + 不可用时段。
        LocalDateTime bufferedStart = startAt.minus(judgeBuffer);
        LocalDateTime bufferedEnd = endAt.plus(judgeBuffer);
        List<Hearing> judgeConflicts = hearingRepository.findJudgeConflicts(
                judge.getId(), bufferedStart, bufferedEnd, excludeHearingId);
        for (Hearing other : judgeConflicts) {
            conflicts.add(judgeConflict(judge, other, startAt, endAt));
        }
        addUnavailableConflicts(conflicts, ResourceType.JUDGE, judge.getId(), judge.getName(),
                startAt, endAt);

        // 法庭：时间冲突 + 不可用 + 容量 + 设施。
        for (Hearing other : hearingRepository.findCourtroomConflicts(
                courtroom.getId(), startAt, endAt, excludeHearingId)) {
            conflicts.add(timeConflict(ResourceType.COURTROOM, courtroom.getId(),
                    courtroom.getName(), other));
        }
        addUnavailableConflicts(conflicts, ResourceType.COURTROOM, courtroom.getId(),
                courtroom.getName(), startAt, endAt);
        if (expectedPeople > courtroom.getCapacity()) {
            conflicts.add(new ConflictDetail(
                    ResourceType.COURTROOM.name(), courtroom.getId(), courtroom.getName(),
                    ConflictReason.CAPACITY,
                    "法庭容量不足：需要 " + expectedPeople + " 人，法庭容量 " + courtroom.getCapacity() + " 人",
                    null, null));
        }
        Set<String> have = SchedulingSupport.normalizeFacilities(courtroom.getFacilities());
        List<String> missing = requiredFacilities.stream()
                .filter(f -> !have.contains(f)).sorted().toList();
        if (!missing.isEmpty()) {
            conflicts.add(new ConflictDetail(
                    ResourceType.COURTROOM.name(), courtroom.getId(), courtroom.getName(),
                    ConflictReason.MISSING_FACILITY,
                    "法庭缺少所需设施: " + String.join(", ", missing),
                    missing, null));
        }

        // 参与人：时间冲突 + 不可用。
        for (Participant p : participants) {
            for (Hearing other : hearingRepository.findParticipantConflicts(
                    p.getId(), startAt, endAt, excludeHearingId)) {
                conflicts.add(timeConflict(ResourceType.PARTICIPANT, p.getId(), p.getName(), other));
            }
            addUnavailableConflicts(conflicts, ResourceType.PARTICIPANT, p.getId(), p.getName(),
                    startAt, endAt);
        }

        // 稳定排序，保证响应顺序确定。
        conflicts.sort(ComparatorProvider.CONFLICT_ORDER);
        return conflicts;
    }

    private ConflictDetail judgeConflict(Judge judge, Hearing other,
                                         LocalDateTime startAt, LocalDateTime endAt) {
        // 直接时间重叠 → TIME_CONFLICT；仅因缓冲扩展而重叠 → BUFFER。
        boolean directOverlap = other.getStartAt().isBefore(endAt) && other.getEndAt().isAfter(startAt);
        String reason = directOverlap ? ConflictReason.TIME_CONFLICT : ConflictReason.BUFFER;
        String message = directOverlap
                ? "法官与案件 " + other.getCaseNo() + " 时间冲突"
                : "法官与相邻案件 " + other.getCaseNo() + " 的缓冲时间不足（需间隔 "
                        + judgeBuffer.toMinutes() + " 分钟）";
        return new ConflictDetail(ResourceType.JUDGE.name(), judge.getId(), judge.getName(),
                reason, message, null, other.getCaseNo());
    }

    private ConflictDetail timeConflict(ResourceType type, Long resourceId, String resourceName,
                                        Hearing other) {
        return new ConflictDetail(type.name(), resourceId, resourceName,
                ConflictReason.TIME_CONFLICT,
                type.label() + "与案件 " + other.getCaseNo() + " 时间冲突",
                null, other.getCaseNo());
    }

    private void addUnavailableConflicts(List<ConflictDetail> conflicts, ResourceType type,
                                         Long resourceId, String resourceName,
                                         LocalDateTime startAt, LocalDateTime endAt) {
        List<UnavailableWindow> windows = unavailableWindowRepository.findOverlapping(
                type, resourceId, startAt, endAt);
        for (UnavailableWindow w : windows) {
            conflicts.add(new ConflictDetail(type.name(), resourceId, resourceName,
                    ConflictReason.UNAVAILABLE,
                    type.label() + "在 " + w.getStartAt() + " 至 " + w.getEndAt() + " 不可用",
                    null, null));
        }
    }

    private void ensureResourceExists(ResourceType type, Long resourceId) {
        boolean exists = switch (type) {
            case JUDGE -> judgeRepository.existsById(resourceId);
            case COURTROOM -> courtroomRepository.existsById(resourceId);
            case PARTICIPANT -> participantRepository.existsById(resourceId);
        };
        if (!exists) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    type.label() + "不存在: id=" + resourceId);
        }
    }

    private static void validateInterval(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始与结束时间不能为空");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "结束时间必须晚于开始时间（区间为左闭右开）");
        }
    }

    private static Set<String> parseFacilities(String joined) {
        if (joined == null || joined.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(List.of(joined.split(",")));
    }

    /** 排期结果：{@code replay} 为 true 表示幂等重放返回的原结果。 */
    public record ScheduleOutcome(HearingResponse hearing, boolean replay) {
    }

    private record LockedResources(Judge judge, Courtroom courtroom, List<Participant> participants) {
    }

    /** 锁内资源的稳定排序器持有者。 */
    private static final class ComparatorProvider {
        private static final java.util.Comparator<ConflictDetail> CONFLICT_ORDER =
                java.util.Comparator.comparing(ConflictDetail::resourceType)
                        .thenComparing(ConflictDetail::resourceId,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                        .thenComparing(ConflictDetail::reason)
                        .thenComparing(ConflictDetail::message);
    }
}
