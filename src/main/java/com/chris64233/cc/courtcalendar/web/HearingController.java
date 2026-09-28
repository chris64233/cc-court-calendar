package com.chris64233.cc.courtcalendar.web;

import com.chris64233.cc.courtcalendar.domain.ResourceType;
import com.chris64233.cc.courtcalendar.error.BusinessException;
import com.chris64233.cc.courtcalendar.error.ErrorCode;
import com.chris64233.cc.courtcalendar.service.SchedulingService;
import com.chris64233.cc.courtcalendar.web.dto.HearingRequest;
import com.chris64233.cc.courtcalendar.web.dto.HearingResponse;
import com.chris64233.cc.courtcalendar.web.dto.RescheduleRequest;
import com.chris64233.cc.courtcalendar.web.dto.ScheduleResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 庭审排期：创建（支持幂等键）、改期、案件详情、按资源查询日程。
 */
@RestController
@RequestMapping("/api/hearings")
public class HearingController {

    private final SchedulingService service;

    public HearingController(SchedulingService service) {
        this.service = service;
    }

    /**
     * 创建排期。请求头 {@code Idempotency-Key} 用于幂等：
     * 相同键 + 相同内容重放返回原结果（200），相同键 + 不同内容返回冲突（409）。
     */
    @PostMapping
    public ResponseEntity<HearingResponse> schedule(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody HearingRequest request) {
        SchedulingService.ScheduleOutcome outcome = service.schedule(request, idempotencyKey);
        HttpStatus status = outcome.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/hearings/" + outcome.hearing().caseNo()))
                .body(outcome.hearing());
    }

    /** 改期：保留原档期校验新时间，成功一次性切换；失败保留原时间。 */
    @PutMapping("/{caseNo}/reschedule")
    public HearingResponse reschedule(@PathVariable String caseNo,
                                      @Valid @RequestBody RescheduleRequest request) {
        return service.reschedule(caseNo, request.startAt(), request.endAt(),
                request.expectedVersion());
    }

    /** 案件排期详情。 */
    @GetMapping("/{caseNo}")
    public HearingResponse detail(@PathVariable String caseNo) {
        return service.getByCaseNo(caseNo);
    }

    /**
     * 按资源查询日程（时间窗内、与窗口有重叠的庭审），稳定排序。
     *
     * @param type JUDGE / COURTROOM / PARTICIPANT
     */
    @GetMapping("/schedule/{type}/{id}")
    public ScheduleResponse scheduleByResource(
            @PathVariable String type,
            @PathVariable Long id,
            @RequestParam LocalDateTime from,
            @RequestParam LocalDateTime to) {
        if (!to.isAfter(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "查询结束时间必须晚于开始时间");
        }
        ResourceType resourceType = parseType(type);
        List<HearingResponse> hearings = service.getSchedule(resourceType, id, from, to);
        return new ScheduleResponse(resourceType.name(), id, from, to, hearings);
    }

    private static ResourceType parseType(String type) {
        try {
            return ResourceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "非法资源类型: " + type + "（支持 JUDGE / COURTROOM / PARTICIPANT）");
        }
    }
}
