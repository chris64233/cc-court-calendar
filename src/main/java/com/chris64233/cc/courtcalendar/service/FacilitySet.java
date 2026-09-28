package com.chris64233.cc.courtcalendar.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.chris64233.cc.courtcalendar.exception.BadRequestException;

/**
 * 设施名称的规范化与匹配。设施名称不区分大小写、忽略首尾空白。
 */
public final class FacilitySet {

    private FacilitySet() {
    }

    /** 规范化为去空白、去空值的集合，保留输入顺序，统一小写存储。 */
    public static Set<String> normalize(Set<String> raw) {
        if (raw == null) {
            return new LinkedHashSet<>();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String facility : raw) {
            if (facility == null || facility.trim().isEmpty()) {
                throw new BadRequestException("设施名称不能为空");
            }
            normalized.add(facility.trim().toLowerCase());
        }
        return normalized;
    }

    /**
     * 计算 {@code available} 相对于 {@code required} 缺失的设施。
     * 返回稳定排序（字典序）的缺失项。
     */
    public static Set<String> missing(Set<String> required, Set<String> available) {
        Set<String> lowerAvailable = available.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        TreeSet<String> missing = new TreeSet<>();
        for (String facility : normalize(required)) {
            if (!lowerAvailable.contains(facility)) {
                missing.add(facility);
            }
        }
        return missing;
    }
}
