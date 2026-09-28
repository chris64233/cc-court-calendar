package com.chris64233.cc.courtcalendar.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.courtcalendar.domain.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
