package com.chris64233.cc.courtcalendar.domain;

/**
 * 可被排期锁定的资源类型，加锁时严格按本枚举声明顺序获取，避免跨事务死锁。
 */
public enum ResourceType {
    JUDGE("法官"),
    COURTROOM("法庭"),
    PARTICIPANT("参与人");

    private final String label;

    ResourceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
