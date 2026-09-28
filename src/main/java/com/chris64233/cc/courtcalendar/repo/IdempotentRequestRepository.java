package com.chris64233.cc.courtcalendar.repo;

import com.chris64233.cc.courtcalendar.domain.IdempotentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, String> {

    /**
     * 排他锁读取幂等记录，保证相同幂等键的并发请求在记录存在时也被串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotentRequest r where r.idempotencyKey = :key")
    java.util.Optional<IdempotentRequest> findByIdForUpdate(@Param("key") String key);
}
