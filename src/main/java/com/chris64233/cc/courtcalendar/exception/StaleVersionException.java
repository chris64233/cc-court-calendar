package com.chris64233.cc.courtcalendar.exception;

/**
 * 改期请求携带的版本号与当前版本不一致，说明基于过期状态做的更新。
 */
public class StaleVersionException extends RuntimeException {

    private final long expectedVersion;
    private final long currentVersion;

    public StaleVersionException(long expectedVersion, long currentVersion) {
        super("排期版本已过期：请求版本 " + expectedVersion + "，当前版本 " + currentVersion);
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
