package com.chris64233.cc.courtcalendar.exception;

/**
 * 相同幂等键但请求内容不同。
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("幂等键已被不同内容的请求使用: " + key);
    }
}
