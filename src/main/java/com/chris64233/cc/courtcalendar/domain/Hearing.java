package com.chris64233.cc.courtcalendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 庭审排期。一场庭审同时占用法官、法庭和全部参与人；案件号全局唯一。
 * {@code version} 为乐观锁版本号，用于拒绝基于过期状态的改期。
 */
@Entity
@Table(name = "hearing",
        uniqueConstraints = @UniqueConstraint(name = "uk_hearing_case_no", columnNames = "case_no"),
        indexes = {
                @Index(name = "idx_hearing_judge_time", columnList = "judge_id,start_at,end_at"),
                @Index(name = "idx_hearing_courtroom_time", columnList = "courtroom_id,start_at,end_at"),
                @Index(name = "idx_hearing_start", columnList = "start_at,end_at")
        })
public class Hearing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_no", nullable = false, unique = true, length = 64)
    private String caseNo;

    @Column(name = "expected_people", nullable = false)
    private int expectedPeople;

    @Column(name = "required_facilities", length = 1024)
    private String requiredFacilities;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "judge_id", nullable = false)
    private Judge judge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courtroom_id", nullable = false)
    private Courtroom courtroom;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "hearing_participant",
            joinColumns = @JoinColumn(name = "hearing_id"),
            inverseJoinColumns = @JoinColumn(name = "participant_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_hearing_participant",
                    columnNames = {"hearing_id", "participant_id"}))
    private Set<Participant> participants = new LinkedHashSet<>();

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Hearing() {
    }

    public Hearing(String caseNo, int expectedPeople, String requiredFacilities, Judge judge,
                   Courtroom courtroom, Set<Participant> participants, LocalDateTime startAt,
                   LocalDateTime endAt) {
        this.caseNo = caseNo;
        this.expectedPeople = expectedPeople;
        this.requiredFacilities = requiredFacilities;
        this.judge = judge;
        this.courtroom = courtroom;
        this.participants = participants == null ? new LinkedHashSet<>() : new LinkedHashSet<>(participants);
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void reschedule(LocalDateTime newStartAt, LocalDateTime newEndAt) {
        this.startAt = newStartAt;
        this.endAt = newEndAt;
    }

    public Long getId() {
        return id;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public int getExpectedPeople() {
        return expectedPeople;
    }

    public String getRequiredFacilities() {
        return requiredFacilities;
    }

    public Judge getJudge() {
        return judge;
    }

    public Courtroom getCourtroom() {
        return courtroom;
    }

    public Set<Participant> getParticipants() {
        return participants;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public long getVersion() {
        return version;
    }
}
