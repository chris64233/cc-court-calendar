package com.chris64233.cc.courtcalendar.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 排期业务参数配置。
 */
@ConfigurationProperties(prefix = "courtcalendar")
public record CourtCalendarProperties(
        /* 法官相邻两场庭审之间必须保留的缓冲时间 */
        Duration judgeBuffer) {

    public CourtCalendarProperties {
        if (judgeBuffer == null) {
            judgeBuffer = Duration.ofMinutes(15);
        }
        if (judgeBuffer.isNegative()) {
            throw new IllegalArgumentException("courtcalendar.judge-buffer 不能为负数");
        }
    }
}
