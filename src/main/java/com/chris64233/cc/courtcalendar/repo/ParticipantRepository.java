package com.chris64233.cc.courtcalendar.repo;

import com.chris64233.cc.courtcalendar.domain.Participant;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    boolean existsByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.id = :id")
    Optional<Participant> findByIdForUpdate(@Param("id") Long id);
}
