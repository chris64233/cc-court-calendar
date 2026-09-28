package com.chris64233.cc.courtcalendar.repo;

import com.chris64233.cc.courtcalendar.domain.Courtroom;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtroomRepository extends JpaRepository<Courtroom, Long> {

    boolean existsByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Courtroom c where c.id = :id")
    Optional<Courtroom> findByIdForUpdate(@Param("id") Long id);
}
