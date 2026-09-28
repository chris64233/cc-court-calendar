package com.chris64233.cc.courtcalendar.error;

import java.util.List;

/** 冲突原因代码，供客户端程序化处理。 */
public final class ConflictReason {

    /** 与其他庭审时间重叠。 */
    public static final String TIME_CONFLICT = "TIME_CONFLICT";
    /** 落入资源的不可用时段。 */
    public static final String UNAVAILABLE = "UNAVAILABLE";
    /** 法庭容量不足。 */
    public static final String CAPACITY = "CAPACITY";
    /** 法庭缺少必需设施。 */
    public static final String MISSING_FACILITY = "MISSING_FACILITY";
    /** 违反法官相邻庭审缓冲时间。 */
    public static final String BUFFER = "BUFFER";

    private ConflictReason() {
    }

    public static List<String> none() {
        return List.of();
    }
}
