package com.chris64233.cc.courtcalendar.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 * 法庭，包含容量和提供的设施集合。
 */
@Entity
@Table(name = "courtroom")
public class Courtroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "courtroom_facility", joinColumns = @JoinColumn(name = "courtroom_id"))
    @Column(name = "facility", length = 64, nullable = false)
    private Set<String> facilities = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "courtroom_unavailability", joinColumns = @JoinColumn(name = "courtroom_id"))
    @OrderColumn(name = "position")
    @AttributeOverrides({
            @AttributeOverride(name = "start", column = @Column(name = "start_at", nullable = false)),
            @AttributeOverride(name = "end", column = @Column(name = "end_at", nullable = false))
    })
    private List<TimeRange> unavailableRanges = new ArrayList<>();

    protected Courtroom() {
    }

    public Courtroom(String name, int capacity, Set<String> facilities) {
        this.name = name;
        this.capacity = capacity;
        if (facilities != null) {
            this.facilities.addAll(facilities);
        }
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public Set<String> getFacilities() {
        return facilities;
    }

    public List<TimeRange> getUnavailableRanges() {
        return unavailableRanges;
    }
}
