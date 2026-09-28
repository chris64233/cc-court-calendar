package com.chris64233.cc.courtcalendar.api;

import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.courtcalendar.exception.BadRequestException;
import com.chris64233.cc.courtcalendar.service.CanonicalRequestHasher;
import com.chris64233.cc.courtcalendar.service.HearingService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 庭审排期接口：排期、原子改期、案件详情、按资源查询日程。
 *
 * <p>排期与改期支持幂等：客户端在 {@code Idempotency-Key} 头中携带键，
 * 相同键 + 相同内容重放返回首次结果；相同键 + 不同内容返回 409。</p>
 */
@RestController
@RequestMapping("/api/hearings")
public class HearingController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final HearingService hearingService;
    private final CanonicalRequestHasher hasher;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public HearingController(HearingService hearingService,
                             CanonicalRequestHasher hasher,
                             ObjectMapper objectMapper,
                             Validator validator) {
        this.hearingService = hearingService;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HearingResponse schedule(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody JsonNode rawBody) {
        String requestHash = hasher.hash(rawBody);
        ScheduleHearingRequest request = convert(rawBody, ScheduleHearingRequest.class);
        return hearingService.schedule(request, normalizeKey(idempotencyKey), requestHash);
    }

    @PutMapping("/{caseNumber}/reschedule")
    public HearingResponse reschedule(
            @PathVariable String caseNumber,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody JsonNode rawBody) {
        String requestHash = hasher.hash(rawBody);
        RescheduleRequest request = convert(rawBody, RescheduleRequest.class);
        return hearingService.reschedule(caseNumber, request,
                normalizeKey(idempotencyKey), requestHash);
    }

    @GetMapping("/{caseNumber}")
    public HearingResponse getByCaseNumber(@PathVariable String caseNumber) {
        return hearingService.getByCaseNumber(caseNumber);
    }

    /**
     * 按资源查询日程：
     * {@code GET /api/hearings?resourceType=judge&resourceId=1}
     */
    @GetMapping
    public List<HearingResponse> getSchedule(@RequestParam String resourceType,
                                             @RequestParam Long resourceId) {
        return hearingService.getSchedule(resourceType.trim().toLowerCase(), resourceId);
    }

    private <T> T convert(JsonNode rawBody, Class<T> type) {
        T request;
        try {
            request = objectMapper.treeToValue(rawBody, type);
        } catch (JacksonException e) {
            throw new BadRequestException("请求体格式不正确: " + e.getOriginalMessage());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return request;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
