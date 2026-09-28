package com.chris64233.cc.courtcalendar.service;

import com.chris64233.cc.courtcalendar.domain.Courtroom;
import com.chris64233.cc.courtcalendar.domain.Hearing;
import com.chris64233.cc.courtcalendar.domain.Participant;
import com.chris64233.cc.courtcalendar.web.dto.CourtroomResponse;
import com.chris64233.cc.courtcalendar.web.dto.HearingResponse;
import com.chris64233.cc.courtcalendar.web.dto.JudgeResponse;
import com.chris64233.cc.courtcalendar.web.dto.ParticipantResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 实体 → 响应 DTO 映射。必须在事务/会话内调用，以便初始化参与人等懒加载关联。
 */
@Component
public class HearingMapper {

    public HearingResponse toHearingResponse(Hearing h) {
        List<ParticipantResponse> participants = h.getParticipants().stream()
                .map(p -> new ParticipantResponse(p.getId(), p.getName()))
                .sorted(java.util.Comparator.comparing(ParticipantResponse::id))
                .toList();
        List<String> facilities = parseFacilities(h.getRequiredFacilities());
        return new HearingResponse(
                h.getId(),
                h.getCaseNo(),
                h.getExpectedPeople(),
                facilities,
                h.getJudge().getId(),
                h.getJudge().getName(),
                h.getCourtroom().getId(),
                h.getCourtroom().getName(),
                participants,
                h.getStartAt(),
                h.getEndAt(),
                h.getVersion());
    }

    public JudgeResponse toJudgeResponse(com.chris64233.cc.courtcalendar.domain.Judge j) {
        return new JudgeResponse(j.getId(), j.getName());
    }

    public CourtroomResponse toCourtroomResponse(Courtroom c) {
        List<String> facilities = c.getFacilities().stream()
                .sorted().toList();
        return new CourtroomResponse(c.getId(), c.getName(), c.getCapacity(), facilities);
    }

    public ParticipantResponse toParticipantResponse(Participant p) {
        return new ParticipantResponse(p.getId(), p.getName());
    }

    private static List<String> parseFacilities(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(joined.split(","))
                .filter(f -> !f.isBlank())
                .sorted()
                .toList();
    }
}
