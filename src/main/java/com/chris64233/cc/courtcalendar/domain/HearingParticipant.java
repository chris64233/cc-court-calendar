package com.chris64233.cc.courtcalendar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 庭审与参与人的关联（一场庭审可占用多名参与人）。
 */
@Entity
@Table(name = "hearing_participant", uniqueConstraints =
        @UniqueConstraint(name = "uk_hearing_participant", columnNames = {"hearing_id", "participant_id"}))
public class HearingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hearing_id", nullable = false)
    private Hearing hearing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    protected HearingParticipant() {
    }

    public HearingParticipant(Hearing hearing, Participant participant) {
        this.hearing = hearing;
        this.participant = participant;
    }

    public Long getId() {
        return id;
    }

    public Hearing getHearing() {
        return hearing;
    }

    public Participant getParticipant() {
        return participant;
    }
}
