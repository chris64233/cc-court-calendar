package com.chris64233.cc.courtcalendar.exception;

/**
 * 客户端请求参数不合法（时间区间非法、参与人重复等）。
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
