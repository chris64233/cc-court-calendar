package com.chris64233.cc.courtcalendar.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 设施名称规范化与请求指纹工具。
 * 设施统一去除空白并转大写后比较；指纹对请求字段做规范化排序，保证语义相同的请求指纹一致。
 */
final class SchedulingSupport {

    private SchedulingSupport() {
    }

    static Set<String> normalizeFacilities(Collection<String> facilities) {
        if (facilities == null) {
            return Set.of();
        }
        return facilities.stream()
                .filter(f -> f != null && !f.isBlank())
                .map(f -> f.trim().toUpperCase())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static String joinFacilities(Collection<String> normalized) {
        return String.join(",", new TreeSet<>(normalized));
    }

    /**
     * 对创建请求的业务字段做 SHA-256 指纹。字段顺序固定、集合排序，避免重放因元素顺序不同而误判。
     */
    static String fingerprint(String caseNo, int expectedPeople, Collection<String> requiredFacilities,
                              Long judgeId, Long courtroomId, Collection<Long> participantIds,
                              String startAt, String endAt) {
        String facilities = String.join(",", new TreeSet<>(
                requiredFacilities == null ? Set.of() : normalizeFacilities(requiredFacilities)));
        String participants = participantIds == null ? ""
                : participantIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        String raw = String.join("|",
                nullToEmpty(caseNo),
                String.valueOf(expectedPeople),
                facilities,
                String.valueOf(judgeId),
                String.valueOf(courtroomId),
                participants,
                nullToEmpty(startAt),
                nullToEmpty(endAt));
        return sha256(raw);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
