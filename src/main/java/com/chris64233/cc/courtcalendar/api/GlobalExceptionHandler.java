package com.chris64233.cc.courtcalendar.api;

import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.chris64233.cc.courtcalendar.exception.BadRequestException;
import com.chris64233.cc.courtcalendar.exception.CaseNumberConflictException;
import com.chris64233.cc.courtcalendar.exception.IdempotencyConflictException;
import com.chris64233.cc.courtcalendar.exception.ResourceConflict;
import com.chris64233.cc.courtcalendar.exception.ResourceNotFoundException;
import com.chris64233.cc.courtcalendar.exception.SchedulingConflictException;
import com.chris64233.cc.courtcalendar.exception.StaleVersionException;

/**
 * 全局异常处理：把业务异常映射为明确的 HTTP 错误，
 * 冲突响应携带冲突资源信息，同时不向客户端泄露内部异常/堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler({BadRequestException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .findFirst()
                .orElse("请求参数校验失败");
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求体格式不正确或缺失",
                request, null);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMissingParameter(Exception ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "请求参数缺失或类型不正确", request, null);
    }

    @ExceptionHandler(SchedulingConflictException.class)
    public ResponseEntity<ErrorResponse> handleSchedulingConflict(SchedulingConflictException ex,
                                                                  HttpServletRequest request) {
        // 冲突资源按类型、ID、原因稳定排序，响应可预测
        List<ResourceConflict> conflicts = ex.getConflicts().stream()
                .sorted(Comparator.comparing(ResourceConflict::resourceType)
                        .thenComparing(ResourceConflict::resourceId)
                        .thenComparing(c -> c.reason().name()))
                .toList();
        return build(HttpStatus.CONFLICT, "SCHEDULING_CONFLICT",
                "排期不满足约束，存在 " + conflicts.size() + " 项资源冲突",
                request, conflicts);
    }

    @ExceptionHandler(CaseNumberConflictException.class)
    public ResponseEntity<ErrorResponse> handleCaseNumberConflict(
            CaseNumberConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CASE_NUMBER_CONFLICT",
                "案件号已被占用: " + ex.getCaseNumber(), request, null);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                "相同幂等键已被不同内容的请求使用", request, null);
    }

    @ExceptionHandler(StaleVersionException.class)
    public ResponseEntity<ErrorResponse> handleStaleVersion(StaleVersionException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "STALE_VERSION",
                "排期已被其他操作修改，请获取最新版本后重试（当前版本 "
                        + ex.getCurrentVersion() + "）", request, null);
    }

    /** JPA 乐观锁在 flush/commit 阶段抛出时的兜底。 */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "STALE_VERSION",
                "排期已被其他操作修改，请获取最新版本后重试", request, null);
    }

    /** 唯一约束等数据库完整性冲突的兜底（如案件号并发重复）。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        // 不把约束名/SQL 状态等内部信息返回给客户端
        log.warn("数据完整性冲突: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "数据冲突，可能是案件号或资源占用重复", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        // 记录完整内部异常用于排查，但只返回通用信息
        log.error("未处理的服务异常", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "服务内部错误，请稍后重试", request, null);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String error,
                                                       String message,
                                                       HttpServletRequest request,
                                                       List<ResourceConflict> conflicts) {
        ErrorResponse body = ErrorResponse.of(status.value(), error, message,
                request.getRequestURI(), conflicts);
        return ResponseEntity.status(status).body(body);
    }
}
