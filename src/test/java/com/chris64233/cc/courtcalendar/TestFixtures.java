package com.chris64233.cc.courtcalendar;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.chris64233.cc.courtcalendar.api.CreateCourtroomRequest;
import com.chris64233.cc.courtcalendar.api.CreateJudgeRequest;
import com.chris64233.cc.courtcalendar.api.CreateParticipantRequest;
import com.chris64233.cc.courtcalendar.api.ScheduleHearingRequest;
import com.chris64233.cc.courtcalendar.api.TimeRangeDto;
import com.chris64233.cc.courtcalendar.service.ResourceService;

/**
 * 测试夹具：快速登记资源、构造排期请求。
 */
@Component
public class TestFixtures {

    private final ResourceService resourceService;

    public TestFixtures(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public Long judge(String name) {
        return judge(name, List.of());
    }

    public Long judge(String name, List<TimeRangeDto> unavailable) {
        return resourceService.createJudge(new CreateJudgeRequest(name, unavailable)).id();
    }

    public Long participant(String name) {
        return participant(name, List.of());
    }

    public Long participant(String name, List<TimeRangeDto> unavailable) {
        return resourceService.createParticipant(
                new CreateParticipantRequest(name, unavailable)).id();
    }

    public Long courtroom(String name, int capacity, String... facilities) {
        return courtroom(name, capacity, List.of(), facilities);
    }

    public Long courtroom(String name, int capacity, List<TimeRangeDto> unavailable,
                          String... facilities) {
        return resourceService.createCourtroom(new CreateCourtroomRequest(
                name, capacity, new LinkedHashSet<>(Set.of(facilities)), unavailable)).id();
    }

    public ScheduleHearingRequest hearing(String caseNumber, int attendees,
                                          Set<String> facilities, Long judgeId,
                                          Long courtroomId, List<Long> participantIds,
                                          String start, String end) {
        return new ScheduleHearingRequest(caseNumber, attendees, facilities, judgeId,
                courtroomId, participantIds,
                java.time.LocalDateTime.parse(start), java.time.LocalDateTime.parse(end));
    }

    public static TimeRangeDto range(String start, String end) {
        return new TimeRangeDto(java.time.LocalDateTime.parse(start),
                java.time.LocalDateTime.parse(end));
    }
}
