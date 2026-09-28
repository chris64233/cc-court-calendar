package com.chris64233.cc.courtcalendar.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.courtcalendar.domain.Hearing;

public interface HearingRepository extends JpaRepository<Hearing, Long> {

    Optional<Hearing> findByCaseNumber(String caseNumber);

    boolean existsByCaseNumber(String caseNumber);

    /** 按开始时间、案件号稳定排序的全部庭审。 */
    @Query("select h from Hearing h order by h.timeRange.start asc, h.caseNumber asc")
    List<Hearing> findAllOrdered();

    @Query("select h from Hearing h where h.judge.id = :judgeId "
            + "order by h.timeRange.start asc, h.caseNumber asc")
    List<Hearing> findScheduleForJudge(@Param("judgeId") Long judgeId);

    @Query("select h from Hearing h where h.courtroom.id = :courtroomId "
            + "order by h.timeRange.start asc, h.caseNumber asc")
    List<Hearing> findScheduleForCourtroom(@Param("courtroomId") Long courtroomId);

    @Query("select distinct h from Hearing h join h.participants hp where hp.participant.id = :participantId "
            + "order by h.timeRange.start asc, h.caseNumber asc")
    List<Hearing> findScheduleForParticipant(@Param("participantId") Long participantId);

    /**
     * 查询与 [rangeStart, rangeEnd) 重叠的某法官庭审（缓冲时间由调用方扩展区间）。
     *
     * @param excludeHearingId 改期校验时排除自身，创建时传 -1
     */
    @Query("select h from Hearing h where h.judge.id = :resourceId "
            + "and h.timeRange.start < :rangeEnd and h.timeRange.end > :rangeStart "
            + "and h.id <> :excludeHearingId")
    List<Hearing> findJudgeConflicts(@Param("resourceId") Long resourceId,
                                     @Param("rangeStart") LocalDateTime rangeStart,
                                     @Param("rangeEnd") LocalDateTime rangeEnd,
                                     @Param("excludeHearingId") Long excludeHearingId);

    /** 查询与 [rangeStart, rangeEnd) 重叠的某法庭庭审。 */
    @Query("select h from Hearing h where h.courtroom.id = :resourceId "
            + "and h.timeRange.start < :rangeEnd and h.timeRange.end > :rangeStart "
            + "and h.id <> :excludeHearingId")
    List<Hearing> findCourtroomConflicts(@Param("resourceId") Long resourceId,
                                         @Param("rangeStart") LocalDateTime rangeStart,
                                         @Param("rangeEnd") LocalDateTime rangeEnd,
                                         @Param("excludeHearingId") Long excludeHearingId);

    /** 查询与 [rangeStart, rangeEnd) 重叠的某参与人庭审。 */
    @Query("select distinct h from Hearing h join h.participants hp "
            + "where hp.participant.id = :resourceId "
            + "and h.timeRange.start < :rangeEnd and h.timeRange.end > :rangeStart "
            + "and h.id <> :excludeHearingId")
    List<Hearing> findParticipantConflicts(@Param("resourceId") Long resourceId,
                                           @Param("rangeStart") LocalDateTime rangeStart,
                                           @Param("rangeEnd") LocalDateTime rangeEnd,
                                           @Param("excludeHearingId") Long excludeHearingId);
}
