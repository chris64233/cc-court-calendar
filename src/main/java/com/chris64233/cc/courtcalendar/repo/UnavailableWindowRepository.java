package com.chris64233.cc.courtcalendar.repo;

import com.chris64233.cc.courtcalendar.domain.ResourceType;
import com.chris64233.cc.courtcalendar.domain.UnavailableWindow;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnavailableWindowRepository extends JpaRepository<UnavailableWindow, Long> {

    /**
     * 区间重叠（左闭右开）：existing.start &lt; end AND existing.end &gt; start。
     */
    @Query("""
            select w from UnavailableWindow w
            where w.resourceType = :type and w.resourceId = :resourceId
              and w.startAt < :endAt and w.endAt > :startAt
            order by w.startAt asc, w.id asc
            """)
    List<UnavailableWindow> findOverlapping(@Param("type") ResourceType type,
                                            @Param("resourceId") Long resourceId,
                                            @Param("startAt") LocalDateTime startAt,
                                            @Param("endAt") LocalDateTime endAt);

    @Query("""
            select w from UnavailableWindow w
            where w.resourceType = :type and w.resourceId = :resourceId
            order by w.startAt asc, w.id asc
            """)
    List<UnavailableWindow> findByResource(@Param("type") ResourceType type,
                                           @Param("resourceId") Long resourceId);
}
