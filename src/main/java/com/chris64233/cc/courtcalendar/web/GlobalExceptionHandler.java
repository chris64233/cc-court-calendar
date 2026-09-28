package com.chris64233.cc.courtcalendar.web;

import com.chris64233.cc.courtcalendar.error.BusinessException;
import com.chris64233.cc.courtcalendar.error.ErrorCode;
import com.chris64233.cc.courtcalendar.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 统一异常处理。所有对外错误均为安全的业务描述，绝不回传堆栈、SQL 或内部异常信息。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex.getErrorCode());
        return ResponseEntity.status(status).body(body(ex.getErrorCode().name(), ex.getMessage(),
                ex.getConflicts(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(GlobalExceptionHandler::formatFieldError)
                .orElse("请求参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(ErrorCode.VALIDATION_ERROR.name(), message, List.of(), request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex,
                                                          HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(ErrorCode.VALIDATION_ERROR.name(), message, List.of(), request));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(ErrorCode.VALIDATION_ERROR.name(), "请求格式或参数有误", List.of(), request));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimistic(ObjectOptimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(ErrorCode.VERSION_CONFLICT.name(),
                        "排期已被其他操作更新，请基于最新版本重试", List.of(), request));
    }

    /** 兜底：对客户端隐藏所有未预期的内部异常细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "服务内部错误，请稍后重试", List.of(), request));
    }

    private static String formatFieldError(FieldError error) {
        return "字段 " + error.getField() + " 不合法：" + error.getDefaultMessage();
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case DUPLICATE_CASE, SCHEDULE_CONFLICT, IDEMPOTENCY_CONFLICT,
                 VERSION_CONFLICT, DUPLICATE_NAME -> HttpStatus.CONFLICT;
        };
    }

    private static ErrorResponse body(String code, String message,
                                      List<com.chris64233.cc.courtcalendar.error.ConflictDetail> conflicts,
                                      HttpServletRequest request) {
        return new ErrorResponse(code, message, conflicts, LocalDateTime.now(),
                request.getRequestURI());
    }
}
