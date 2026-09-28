package com.chris64233.cc.courtcalendar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.courtcalendar.domain.Courtroom;

public interface CourtroomRepository extends JpaRepository<Courtroom, Long> {

    List<Courtroom> findAllByOrderByIdAsc();
}
