package com.chris64233.cc.courtcalendar.api;

/**
 * 庭审详情中对参与人的精简引用（不含参与人自身的不可用时段）。
 */
public record ParticipantRef(Long id, String name) {
}
