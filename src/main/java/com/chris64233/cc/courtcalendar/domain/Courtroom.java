package com.chris64233.cc.courtcalendar.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 法庭。拥有容量与可用设施集合。
 */
@Entity
@Table(name = "courtroom")
public class Courtroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 128)
    private String name;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "courtroom_facility", joinColumns = @JoinColumn(name = "courtroom_id"))
    @Column(name = "facility", length = 64)
    private Set<String> facilities = new LinkedHashSet<>();

    protected Courtroom() {
    }

    public Courtroom(String name, int capacity, Set<String> facilities) {
        this.name = name;
        this.capacity = capacity;
        this.facilities = facilities == null ? new LinkedHashSet<>() : new LinkedHashSet<>(facilities);
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
}
