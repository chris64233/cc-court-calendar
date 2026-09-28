package com.chris64233.cc.courtcalendar.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.courtcalendar.service.ResourceService;

/**
 * 法官、法庭、参与人的登记接口。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    // ---- 法官 ----

    @PostMapping("/judges")
    @ResponseStatus(HttpStatus.CREATED)
    public JudgeResponse createJudge(@Valid @RequestBody CreateJudgeRequest request) {
        return resourceService.createJudge(request);
    }

    @GetMapping("/judges")
    public List<JudgeResponse> listJudges() {
        return resourceService.listJudges();
    }

    @GetMapping("/judges/{id}")
    public JudgeResponse getJudge(@PathVariable Long id) {
        return resourceService.getJudge(id);
    }

    @PostMapping("/judges/{id}/unavailability")
    public JudgeResponse addJudgeUnavailability(@PathVariable Long id,
                                                @Valid @RequestBody TimeRangeDto range) {
        return resourceService.addJudgeUnavailability(id, range);
    }

    // ---- 法庭 ----

    @PostMapping("/courtrooms")
    @ResponseStatus(HttpStatus.CREATED)
    public CourtroomResponse createCourtroom(@Valid @RequestBody CreateCourtroomRequest request) {
        return resourceService.createCourtroom(request);
    }

    @GetMapping("/courtrooms")
    public List<CourtroomResponse> listCourtrooms() {
        return resourceService.listCourtrooms();
    }

    @GetMapping("/courtrooms/{id}")
    public CourtroomResponse getCourtroom(@PathVariable Long id) {
        return resourceService.getCourtroom(id);
    }

    @PostMapping("/courtrooms/{id}/unavailability")
    public CourtroomResponse addCourtroomUnavailability(@PathVariable Long id,
                                                        @Valid @RequestBody TimeRangeDto range) {
        return resourceService.addCourtroomUnavailability(id, range);
    }

    // ---- 参与人 ----

    @PostMapping("/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipantResponse createParticipant(
            @Valid @RequestBody CreateParticipantRequest request) {
        return resourceService.createParticipant(request);
    }

    @GetMapping("/participants")
    public List<ParticipantResponse> listParticipants() {
        return resourceService.listParticipants();
    }

    @GetMapping("/participants/{id}")
    public ParticipantResponse getParticipant(@PathVariable Long id) {
        return resourceService.getParticipant(id);
    }

    @PostMapping("/participants/{id}/unavailability")
    public ParticipantResponse addParticipantUnavailability(@PathVariable Long id,
                                                            @Valid @RequestBody TimeRangeDto range) {
        return resourceService.addParticipantUnavailability(id, range);
    }
}
