package com.chris64233.cc.courtcalendar.repo;

import com.chris64233.cc.courtcalendar.domain.Hearing;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HearingRepository extends JpaRepository<Hearing, Long> {

    Optional<Hearing> findByCaseNo(String caseNo);

    boolean existsByCaseNo(String caseNo);

    /**
     * 法官冲突庭审：在开庭前后各扩展缓冲时间后再做重叠判断，保证相邻庭审之间留有缓冲。
     * 改期时通过 {@code excludeId} 排除自身原档期。
     */
    @Query("""
            select h from Hearing h
            where h.judge.id = :judgeId
              and (:excludeId is null or h.id <> :excludeId)
              and h.startAt < :bufferedEnd and h.endAt > :bufferedStart
            order by h.startAt asc, h.id asc
            """)
    List<Hearing> findJudgeConflicts(@Param("judgeId") Long judgeId,
                                     @Param("bufferedStart") LocalDateTime bufferedStart,
                                     @Param("bufferedEnd") LocalDateTime bufferedEnd,
                                     @Param("excludeId") Long excludeId);

    /**
     * 法庭冲突庭审：普通左闭右开重叠，无缓冲。
     */
    @Query("""
            select h from Hearing h
            where h.courtroom.id = :courtroomId
              and (:excludeId is null or h.id <> :excludeId)
              and h.startAt < :endAt and h.endAt > :startAt
            order by h.startAt asc, h.id asc
            """)
    List<Hearing> findCourtroomConflicts(@Param("courtroomId") Long courtroomId,
                                         @Param("startAt") LocalDateTime startAt,
                                         @Param("endAt") LocalDateTime endAt,
                                         @Param("excludeId") Long excludeId);

    /**
     * 参与人冲突庭审（多对多关联表）。
     */
    @Query("""
            select h from Hearing h
            join h.participants p
            where p.id = :participantId
              and (:excludeId is null or h.id <> :excludeId)
              and h.startAt < :endAt and h.endAt > :startAt
            order by h.startAt asc, h.id asc
            """)
    List<Hearing> findParticipantConflicts(@Param("participantId") Long participantId,
                                           @Param("startAt") LocalDateTime startAt,
                                           @Param("endAt") LocalDateTime endAt,
                                           @Param("excludeId") Long excludeId);

    /**
     * 法官日程，稳定排序：开始时间、结束时间、庭审 id。
     */
    @Query("""
            select h from Hearing h
            where h.judge.id = :resourceId
              and h.startAt < :endAt and h.endAt > :startAt
            order by h.startAt asc, h.endAt asc, h.id asc
            """)
    List<Hearing> findJudgeSchedule(@Param("resourceId") Long resourceId,
                                    @Param("startAt") LocalDateTime startAt,
                                    @Param("endAt") LocalDateTime endAt);

    @Query("""
            select h from Hearing h
            where h.courtroom.id = :resourceId
              and h.startAt < :endAt and h.endAt > :startAt
            order by h.startAt asc, h.endAt asc, h.id asc
            """)
    List<Hearing> findCourtroomSchedule(@Param("resourceId") Long resourceId,
                                        @Param("startAt") LocalDateTime startAt,
                                        @Param("endAt") LocalDateTime endAt);

    @Query("""
            select h from Hearing h
            join h.participants p
            where p.id = :resourceId
              and h.startAt < :endAt and h.endAt > :startAt
            order by h.startAt asc, h.endAt asc, h.id asc
            """)
    List<Hearing> findParticipantSchedule(@Param("resourceId") Long resourceId,
                                          @Param("startAt") LocalDateTime startAt,
                                          @Param("endAt") LocalDateTime endAt);
}
