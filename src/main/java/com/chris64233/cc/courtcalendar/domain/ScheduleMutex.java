package com.chris64233.cc.courtcalendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 排期串行化互斥锁。
 *
 * <p>所有创建/改期排期的事务在开始时通过 {@code SELECT ... FOR UPDATE}
 * 锁定唯一的这一行，使并发请求在数据库层面排队，配合可重复读保证
 * “先检查后写入”不会产生双重占用。</p>
 */
@Entity
@Table(name = "schedule_mutex")
public class ScheduleMutex {

    /** 固定主键，全表只有一行。 */
    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id = SINGLETON_ID;

    protected ScheduleMutex() {
    }

    public ScheduleMutex(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
