package com.chris64233.cc.courtcalendar.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.chris64233.cc.courtcalendar.config.CourtCalendarProperties;
import com.chris64233.cc.courtcalendar.domain.Courtroom;
import com.chris64233.cc.courtcalendar.domain.Hearing;
import com.chris64233.cc.courtcalendar.domain.Judge;
import com.chris64233.cc.courtcalendar.domain.Participant;
import com.chris64233.cc.courtcalendar.domain.TimeRange;
import com.chris64233.cc.courtcalendar.exception.ConflictReason;
import com.chris64233.cc.courtcalendar.exception.ResourceConflict;
import com.chris64233.cc.courtcalendar.repository.HearingRepository;

/**
 * 排期冲突校验。
 *
 * <p>校验规则：</p>
 * <ul>
 *   <li>法官 / 法庭 / 每名参与人的已有庭审时间不得重叠（左闭右开，端点相接不冲突）；</li>
 *   <li>时间不得落入任何资源的不可用时段；</li>
 *   <li>法官相邻庭审之间必须满足配置的缓冲时间；</li>
 *   <li>法庭容量 ≥ 预计人数，且设施集合包含全部所需设施。</li>
 * </ul>
 * 必须在持有排期互斥锁的事务内调用，以保证“检查—写入”的原子性。
 */
@Component
public class ConflictValidator {

    private static final Comparator<Hearing> BY_START =
            Comparator.comparing(Hearing::getStart).thenComparing(Hearing::getCaseNumber);

    private final HearingRepository hearingRepository;
    private final Duration judgeBuffer;

    public ConflictValidator(HearingRepository hearingRepository,
                             CourtCalendarProperties properties) {
        this.hearingRepository = hearingRepository;
        this.judgeBuffer = properties.judgeBuffer();
    }

    /**
     * 校验候选排期。
     *
     * @param excludeHearingId 创建排期时传 null；改期时传被改庭审自身的 ID
     * @return 全部冲突（顺序稳定：法官 → 法庭 → 参与人按 ID 升序）；为空表示可以排期
     */
    public List<ResourceConflict> validate(TimeRange target,
                                           Judge judge,
                                           Courtroom courtroom,
                                           List<Participant> participants,
                                           int expectedAttendees,
                                           Set<String> requiredFacilities,
                                           Long excludeHearingId) {
        long selfId = excludeHearingId == null ? -1L : excludeHearingId;
        LocalDateTime start = target.getStart();
        LocalDateTime end = target.getEnd();

        List<ResourceConflict> conflicts = new ArrayList<>();
        validateJudge(target, judge, selfId, conflicts);
        validateCourtroom(target, courtroom, expectedAttendees, requiredFacilities, selfId, conflicts);
        participants.stream()
                .sorted(Comparator.comparing(Participant::getId))
                .forEach(participant ->
                        validateParticipant(target, participant, selfId, conflicts));
        return conflicts;
    }

    private void validateJudge(TimeRange target, Judge judge, long selfId,
                               List<ResourceConflict> conflicts) {
        LocalDateTime bufferedStart = target.getStart().minus(judgeBuffer);
        LocalDateTime bufferedEnd = target.getEnd().plus(judgeBuffer);

        List<Hearing> neighbors = hearingRepository.findJudgeConflicts(
                judge.getId(), bufferedStart, bufferedEnd, selfId);
        neighbors.sort(BY_START);

        List<String> occupiedCases = new ArrayList<>();
        List<String> bufferCases = new ArrayList<>();
        for (Hearing other : neighbors) {
            if (target.overlaps(other.getTimeRange())) {
                occupiedCases.add(other.getCaseNumber());
            } else if (!judgeBuffer.isZero()) {
                bufferCases.add(other.getCaseNumber());
            }
        }

        if (!occupiedCases.isEmpty()) {
            conflicts.add(new ResourceConflict(
                    "judge", judge.getId(), judge.getName(), ConflictReason.OCCUPIED,
                    occupiedCases.get(0),
                    "法官在该时间段已有庭审: " + joinCases(occupiedCases)));
        }
        if (!bufferCases.isEmpty()) {
            conflicts.add(new ResourceConflict(
                    "judge", judge.getId(), judge.getName(), ConflictReason.BUFFER,
                    bufferCases.get(0),
                    "法官相邻庭审之间需要 " + judgeBuffer.toMinutes()
                            + " 分钟缓冲时间，相邻案件: " + joinCases(bufferCases)));
        }

        if (hitsUnavailability(target, judge.getUnavailableRanges())) {
            conflicts.add(new ResourceConflict(
                    "judge", judge.getId(), judge.getName(), ConflictReason.UNAVAILABLE,
                    null, "法官在该时间段不可用"));
        }
    }

    private void validateCourtroom(TimeRange target, Courtroom courtroom,
                                   int expectedAttendees, Set<String> requiredFacilities,
                                   long selfId, List<ResourceConflict> conflicts) {
        List<Hearing> occupied = hearingRepository.findCourtroomConflicts(
                courtroom.getId(), target.getStart(), target.getEnd(), selfId);
        occupied.sort(BY_START);
        if (!occupied.isEmpty()) {
            List<String> cases = occupied.stream().map(Hearing::getCaseNumber).toList();
            conflicts.add(new ResourceConflict(
                    "courtroom", courtroom.getId(), courtroom.getName(),
                    ConflictReason.OCCUPIED, cases.get(0),
                    "法庭在该时间段已被占用: " + joinCases(cases)));
        }

        if (hitsUnavailability(target, courtroom.getUnavailableRanges())) {
            conflicts.add(new ResourceConflict(
                    "courtroom", courtroom.getId(), courtroom.getName(),
                    ConflictReason.UNAVAILABLE, null, "法庭在该时间段不可用"));
        }

        if (expectedAttendees > courtroom.getCapacity()) {
            conflicts.add(new ResourceConflict(
                    "courtroom", courtroom.getId(), courtroom.getName(),
                    ConflictReason.CAPACITY, null,
                    "法庭容量不足: 需要 " + expectedAttendees + " 人，容量 "
                            + courtroom.getCapacity() + " 人"));
        }

        Set<String> missing = FacilitySet.missing(requiredFacilities, courtroom.getFacilities());
        if (!missing.isEmpty()) {
            conflicts.add(new ResourceConflict(
                    "courtroom", courtroom.getId(), courtroom.getName(),
                    ConflictReason.FACILITY, null,
                    "法庭缺少所需设施: " + String.join(", ", missing)));
        }
    }

    private void validateParticipant(TimeRange target, Participant participant,
                                     long selfId, List<ResourceConflict> conflicts) {
        List<Hearing> occupied = hearingRepository.findParticipantConflicts(
                participant.getId(), target.getStart(), target.getEnd(), selfId);
        occupied.sort(BY_START);
        if (!occupied.isEmpty()) {
            List<String> cases = occupied.stream().map(Hearing::getCaseNumber).toList();
            conflicts.add(new ResourceConflict(
                    "participant", participant.getId(), participant.getName(),
                    ConflictReason.OCCUPIED, cases.get(0),
                    "参与人在该时间段已有庭审: " + joinCases(cases)));
        }

        if (hitsUnavailability(target, participant.getUnavailableRanges())) {
            conflicts.add(new ResourceConflict(
                    "participant", participant.getId(), participant.getName(),
                    ConflictReason.UNAVAILABLE, null, "参与人在该时间段不可用"));
        }
    }

    private static boolean hitsUnavailability(TimeRange target, List<TimeRange> unavailable) {
        return unavailable.stream().anyMatch(target::overlaps);
    }

    /** 案件号去重后稳定排序拼接。 */
    private static String joinCases(List<String> caseNumbers) {
        Set<String> unique = new TreeSet<>(caseNumbers);
        return String.join(", ", unique);
    }
}
