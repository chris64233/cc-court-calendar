package com.chris64233.cc.courtcalendar.repo;

import com.chris64233.cc.courtcalendar.domain.Judge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JudgeRepository extends JpaRepository<Judge, Long> {

    boolean existsByName(String name);

    /**
     * 排他行锁：使所有涉及同一法官的排期事务串行化，从根本上杜绝双重占用。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Judge j where j.id = :id")
    Optional<Judge> findByIdForUpdate(@Param("id") Long id);
}
