package com.chris64233.cc.courtcalendar.exception;

/**
 * 冲突原因分类。
 */
public enum ConflictReason {

    /** 资源在该时间段已有另一场庭审。 */
    OCCUPIED,

    /** 落入资源的不可用时段。 */
    UNAVAILABLE,

    /** 法官相邻庭审之间的缓冲时间不足。 */
    BUFFER,

    /** 法庭容量不足。 */
    CAPACITY,

    /** 法庭缺少所需设施。 */
    FACILITY
}
