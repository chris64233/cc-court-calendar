package com.chris64233.cc.courtcalendar.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.courtcalendar.api.CreateCourtroomRequest;
import com.chris64233.cc.courtcalendar.api.CreateJudgeRequest;
import com.chris64233.cc.courtcalendar.api.CreateParticipantRequest;
import com.chris64233.cc.courtcalendar.api.CourtroomResponse;
import com.chris64233.cc.courtcalendar.api.JudgeResponse;
import com.chris64233.cc.courtcalendar.api.ParticipantResponse;
import com.chris64233.cc.courtcalendar.api.TimeRangeDto;
import com.chris64233.cc.courtcalendar.domain.Courtroom;
import com.chris64233.cc.courtcalendar.domain.Judge;
import com.chris64233.cc.courtcalendar.domain.Participant;
import com.chris64233.cc.courtcalendar.domain.TimeRange;
import com.chris64233.cc.courtcalendar.exception.BadRequestException;
import com.chris64233.cc.courtcalendar.exception.ResourceNotFoundException;
import com.chris64233.cc.courtcalendar.repository.CourtroomRepository;
import com.chris64233.cc.courtcalendar.repository.JudgeRepository;
import com.chris64233.cc.courtcalendar.repository.ParticipantRepository;

/**
 * 法官、法庭、参与人的登记与查询。
 */
@Service
public class ResourceService {

    private final JudgeRepository judgeRepository;
    private final CourtroomRepository courtroomRepository;
    private final ParticipantRepository participantRepository;

    public ResourceService(JudgeRepository judgeRepository,
                           CourtroomRepository courtroomRepository,
                           ParticipantRepository participantRepository) {
        this.judgeRepository = judgeRepository;
        this.courtroomRepository = courtroomRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public JudgeResponse createJudge(CreateJudgeRequest request) {
        Judge judge = new Judge(request.name().trim());
        request.unavailableRanges().forEach(dto -> judge.addUnavailability(toTimeRange(dto)));
        return toJudgeResponse(judgeRepository.save(judge));
    }

    @Transactional
    public ParticipantResponse createParticipant(CreateParticipantRequest request) {
        Participant participant = new Participant(request.name().trim());
        request.unavailableRanges().forEach(dto -> participant.addUnavailability(toTimeRange(dto)));
        return toParticipantResponse(participantRepository.save(participant));
    }

    @Transactional
    public CourtroomResponse createCourtroom(CreateCourtroomRequest request) {
        Courtroom courtroom = new Courtroom(request.name().trim(), request.capacity(),
                FacilitySet.normalize(request.facilities()));
        request.unavailableRanges().forEach(dto -> courtroom.addUnavailability(toTimeRange(dto)));
        return toCourtroomResponse(courtroomRepository.save(courtroom));
    }

    @Transactional
    public JudgeResponse addJudgeUnavailability(Long id, TimeRangeDto dto) {
        Judge judge = getJudgeOrThrow(id);
        judge.addUnavailability(toTimeRange(dto));
        return toJudgeResponse(judge);
    }

    @Transactional
    public ParticipantResponse addParticipantUnavailability(Long id, TimeRangeDto dto) {
        Participant participant = getParticipantOrThrow(id);
        participant.addUnavailability(toTimeRange(dto));
        return toParticipantResponse(participant);
    }

    @Transactional
    public CourtroomResponse addCourtroomUnavailability(Long id, TimeRangeDto dto) {
        Courtroom courtroom = getCourtroomOrThrow(id);
        courtroom.addUnavailability(toTimeRange(dto));
        return toCourtroomResponse(courtroom);
    }

    @Transactional(readOnly = true)
    public JudgeResponse getJudge(Long id) {
        return toJudgeResponse(getJudgeOrThrow(id));
    }

    @Transactional(readOnly = true)
    public ParticipantResponse getParticipant(Long id) {
        return toParticipantResponse(getParticipantOrThrow(id));
    }

    @Transactional(readOnly = true)
    public CourtroomResponse getCourtroom(Long id) {
        return toCourtroomResponse(getCourtroomOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<JudgeResponse> listJudges() {
        return judgeRepository.findAllByOrderByIdAsc().stream().map(this::toJudgeResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> listParticipants() {
        return participantRepository.findAllByOrderByIdAsc().stream()
                .map(this::toParticipantResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CourtroomResponse> listCourtrooms() {
        return courtroomRepository.findAllByOrderByIdAsc().stream()
                .map(this::toCourtroomResponse).toList();
    }

    /* ---- 实体级查找（供排期服务在同一事务内使用） ---- */

    @Transactional(readOnly = true)
    public Judge requireJudgeEntity(Long id) {
        return getJudgeOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Courtroom requireCourtroomEntity(Long id) {
        return getCourtroomOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<Participant> requireParticipantEntities(List<Long> ids) {
        if (ids.stream().distinct().count() != ids.size()) {
            throw new BadRequestException("参与人列表存在重复 ID");
        }
        return ids.stream().map(this::getParticipantOrThrow).toList();
    }

    private Judge getJudgeOrThrow(Long id) {
        return judgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("法官", id));
    }

    private Participant getParticipantOrThrow(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("参与人", id));
    }

    private Courtroom getCourtroomOrThrow(Long id) {
        return courtroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("法庭", id));
    }

    public static TimeRange toTimeRange(TimeRangeDto dto) {
        try {
            return new TimeRange(dto.start(), dto.end());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private JudgeResponse toJudgeResponse(Judge judge) {
        return new JudgeResponse(judge.getId(), judge.getName(),
                judge.getUnavailableRanges().stream()
                        .map(r -> new TimeRangeDto(r.getStart(), r.getEnd())).toList());
    }

    private ParticipantResponse toParticipantResponse(Participant participant) {
        return new ParticipantResponse(participant.getId(), participant.getName(),
                participant.getUnavailableRanges().stream()
                        .map(r -> new TimeRangeDto(r.getStart(), r.getEnd())).toList());
    }

    private CourtroomResponse toCourtroomResponse(Courtroom courtroom) {
        return new CourtroomResponse(courtroom.getId(), courtroom.getName(),
                courtroom.getCapacity(),
                FacilitySet.normalize(courtroom.getFacilities()),
                courtroom.getUnavailableRanges().stream()
                        .map(r -> new TimeRangeDto(r.getStart(), r.getEnd())).toList());
    }
}
