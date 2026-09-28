package com.chris64233.cc.courtcalendar.error;

import java.util.List;

/**
 * 业务异常。携带错误码、对外安全的信息以及冲突资源明细。
 * 永远不向客户端暴露内部异常与堆栈。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<ConflictDetail> conflicts;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<ConflictDetail> conflicts) {
        super(message);
        this.errorCode = errorCode;
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<ConflictDetail> getConflicts() {
        return conflicts;
    }
}
