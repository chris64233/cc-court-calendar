package com.chris64233.cc.courtcalendar.web;

import com.chris64233.cc.courtcalendar.domain.ResourceType;
import com.chris64233.cc.courtcalendar.error.BusinessException;
import com.chris64233.cc.courtcalendar.error.ErrorCode;
import com.chris64233.cc.courtcalendar.service.SchedulingService;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomRequest;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomResponse;
import com.chris64233.cc.courtcalendar.web.dto.JudgeRequest;
import com.chris64233.cc.courtcalendar.web.dto.JudgeResponse;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantRequest;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantResponse;
import com.chris64233.cc.courtcalendar.web.dto.UnavailableWindowRequest;
import com.chris64233.cc.courtcalendar.web.dto.UnavailableWindowResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源登记：法官、法庭、参与人及其不可用时段。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final SchedulingService service;

    public ResourceController(SchedulingService service) {
        this.service = service;
    }

    @PostMapping("/judges")
    public ResponseEntity<JudgeResponse> registerJudge(@Valid @RequestBody JudgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerJudge(request.name()));
    }

    @PostMapping("/courtrooms")
    public ResponseEntity<CourtroomResponse> registerCourtroom(
            @Valid @RequestBody CourtroomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registerCourtroom(request.name(), request.capacity(),
                        request.facilities()));
    }

    @PostMapping("/participants")
    public ResponseEntity<ParticipantResponse> registerParticipant(
            @Valid @RequestBody ParticipantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerParticipant(request.name()));
    }

    @PostMapping("/{type}/{id}/unavailable")
    public ResponseEntity<UnavailableWindowResponse> addUnavailable(
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody UnavailableWindowRequest request) {
        ResourceType resourceType = parseType(type);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addUnavailableWindow(resourceType, id, request.startAt(),
                        request.endAt(), request.reason()));
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
