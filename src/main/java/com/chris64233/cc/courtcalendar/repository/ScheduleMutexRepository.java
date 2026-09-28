package com.chris64233.cc.courtcalendar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.courtcalendar.domain.ScheduleMutex;

import jakarta.persistence.LockModeType;

public interface ScheduleMutexRepository extends JpaRepository<ScheduleMutex, Long> {

    /**
     * 悲观写锁：排期/改期事务持有该锁直到提交，
     * 确保并发排期请求在数据库层面串行化，不会形成双重占用。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ScheduleMutex m where m.id = :id")
    ScheduleMutex lockById(@Param("id") long id);
}
