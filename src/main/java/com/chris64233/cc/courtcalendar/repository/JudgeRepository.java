package com.chris64233.cc.courtcalendar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.courtcalendar.domain.Judge;

public interface JudgeRepository extends JpaRepository<Judge, Long> {

    List<Judge> findAllByOrderByIdAsc();
}
