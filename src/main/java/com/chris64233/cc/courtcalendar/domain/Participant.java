package com.chris64233.cc.courtcalendar.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * 参与人（书记员、公诉人、律师、当事人等）。
 */
@Entity
@Table(name = "participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "participant_unavailability", joinColumns = @JoinColumn(name = "participant_id"))
    @OrderColumn(name = "position")
    @AttributeOverrides({
            @AttributeOverride(name = "start", column = @Column(name = "start_at", nullable = false)),
            @AttributeOverride(name = "end", column = @Column(name = "end_at", nullable = false))
    })
    private List<TimeRange> unavailableRanges = new ArrayList<>();

    protected Participant() {
    }

    public Participant(String name) {
        this.name = name;
    }

    public void addUnavailability(TimeRange range) {
        unavailableRanges.add(range);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TimeRange> getUnavailableRanges() {
        return unavailableRanges;
    }
}
