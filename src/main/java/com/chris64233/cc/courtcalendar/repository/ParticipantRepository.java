package com.chris64233.cc.courtcalendar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.courtcalendar.domain.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findAllByOrderByIdAsc();
}
