package com.chris64233.cc.courtcalendar.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 庭审排期。一次排期同时占用法官、法庭和多名参与人。
 *
 * <p>时间区间为左闭右开。{@code version} 字段用于乐观锁，拒绝基于过期状态的改期。</p>
 */
@Entity
@Table(name = "hearing")
public class Hearing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 案件号，全局唯一。 */
    @Column(name = "case_number", nullable = false, unique = true, length = 64)
    private String caseNumber;

    /** 预计参加庭审的人数（需满足法庭容量）。 */
    @Column(name = "expected_attendees", nullable = false)
    private int expectedAttendees;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "hearing_required_facility", joinColumns = @JoinColumn(name = "hearing_id"))
    @Column(name = "facility", length = 64, nullable = false)
    private Set<String> requiredFacilities = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "judge_id", nullable = false)
    private Judge judge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courtroom_id", nullable = false)
    private Courtroom courtroom;

    @OneToMany(mappedBy = "hearing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<HearingParticipant> participants = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "start", column = @Column(name = "start_at", nullable = false)),
            @AttributeOverride(name = "end", column = @Column(name = "end_at", nullable = false))
    })
    private TimeRange timeRange;

    /** JPA 乐观锁版本号，改期时必须携带，防止过期更新。 */
    @Version
    private long version;

    protected Hearing() {
    }

    public Hearing(String caseNumber, int expectedAttendees, Set<String> requiredFacilities,
                   Judge judge, Courtroom courtroom, TimeRange timeRange) {
        this.caseNumber = caseNumber;
        this.expectedAttendees = expectedAttendees;
        if (requiredFacilities != null) {
            this.requiredFacilities.addAll(requiredFacilities);
        }
        this.judge = judge;
        this.courtroom = courtroom;
        this.timeRange = timeRange;
    }

    public void addParticipant(Participant participant) {
        participants.add(new HearingParticipant(this, participant));
    }

    /**
     * 改期：只更新时间区间，参与人/法官/法庭等资源保持不变。
     * 调用方必须保证新时间已通过全部冲突校验。
     */
    public void reschedule(TimeRange newRange) {
        this.timeRange = newRange;
    }

    public Long getId() {
        return id;
    }

    public String getCaseNumber() {
        return caseNumber;
    }

    public int getExpectedAttendees() {
        return expectedAttendees;
    }

    public Set<String> getRequiredFacilities() {
        return requiredFacilities;
    }

    public Judge getJudge() {
        return judge;
    }

    public Courtroom getCourtroom() {
        return courtroom;
    }

    public List<HearingParticipant> getParticipants() {
        return participants;
    }

    public LocalDateTime getStart() {
        return timeRange.getStart();
    }

    public LocalDateTime getEnd() {
        return timeRange.getEnd();
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public long getVersion() {
        return version;
    }
}
