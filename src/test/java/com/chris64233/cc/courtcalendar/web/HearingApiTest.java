package com.chris64233.cc.courtcalendar.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class HearingApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 2, 9, 0);

    private long register(String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> hearingBody(String caseNo, long judge, long room,
                                            Set<Long> participants, LocalDateTime s, LocalDateTime e,
                                            int people, Set<String> facilities) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseNo", caseNo);
        body.put("expectedPeople", people);
        body.put("requiredFacilities", facilities);
        body.put("judgeId", judge);
        body.put("courtroomId", room);
        body.put("participantIds", participants);
        body.put("startAt", s.toString());
        body.put("endAt", e.toString());
        return body;
    }

    @Test
    void fullFlow_scheduleConflictDetailAndReschedule() throws Exception {
        long judge = register("/api/resources/judges", "{\"name\":\"WJ-" + System.nanoTime() + "\"}");
        long room = register("/api/resources/courtrooms",
                "{\"name\":\"WR-" + System.nanoTime() + "\",\"capacity\":10,\"facilities\":[\"RECORDING\"]}");
        long p1 = register("/api/resources/participants", "{\"name\":\"WP1-" + System.nanoTime() + "\"}");
        long p2 = register("/api/resources/participants", "{\"name\":\"WP2-" + System.nanoTime() + "\"}");

        Map<String, Object> first = hearingBody("WEB-1", judge, room, Set.of(p1, p2),
                T, T.plusHours(1), 5, Set.of("RECORDING"));

        // 首次创建：201，含 Location。
        mockMvc.perform(post("/api/hearings")
                        .header("Idempotency-Key", "web-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/hearings/WEB-1"))
                .andExpect(jsonPath("$.version").value(0));

        // 相同幂等键 + 相同内容重放：200，同一 id。
        mockMvc.perform(post("/api/hearings")
                        .header("Idempotency-Key", "web-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNo").value("WEB-1"));

        // 相同幂等键 + 不同内容：409，错误码 IDEMPOTENCY_CONFLICT。
        Map<String, Object> changed = hearingBody("WEB-1", judge, room, Set.of(p1, p2),
                T.plusHours(3), T.plusHours(4), 5, Set.of("RECORDING"));
        mockMvc.perform(post("/api/hearings")
                        .header("Idempotency-Key", "web-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        // 时间冲突：409，冲突明细定位到具体资源与对方案件，且不含内部异常字段。
        Map<String, Object> clash = hearingBody("WEB-2", judge, room, Set.of(p1),
                T.plusMinutes(30), T.plusMinutes(90), 5, Set.of("RECORDING"));
        mockMvc.perform(post("/api/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clash)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.conflicts[0].resourceType").exists())
                .andExpect(jsonPath("$.conflicts[?(@.reason=='TIME_CONFLICT')]").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());

        // 改期成功：版本号 0 -> 1。
        mockMvc.perform(put("/api/hearings/WEB-1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startAt":"%s","endAt":"%s","expectedVersion":0}"""
                                .formatted(T.plusHours(4), T.plusHours(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.startAt").value(T.plusHours(4).toString()));

        // 用过期版本再改：409 VERSION_CONFLICT。
        mockMvc.perform(put("/api/hearings/WEB-1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startAt":"%s","endAt":"%s","expectedVersion":0}"""
                                .formatted(T.plusHours(6), T.plusHours(7))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 详情。
        mockMvc.perform(get("/api/hearings/WEB-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // 法官日程稳定排序。
        mockMvc.perform(get("/api/hearings/schedule/JUDGE/" + judge)
                        .param("from", T.minusHours(1).toString())
                        .param("to", T.plusHours(8).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("JUDGE"))
                .andExpect(jsonPath("$.hearings[0].caseNo").value("WEB-1"));
    }

    @Test
    void capacityAndFacilityConflictsAreReported() throws Exception {
        long judge = register("/api/resources/judges", "{\"name\":\"CJ2-" + System.nanoTime() + "\"}");
        long room = register("/api/resources/courtrooms",
                "{\"name\":\"CR2-" + System.nanoTime() + "\",\"capacity\":5,\"facilities\":[\"RECORDING\"]}");
        long p = register("/api/resources/participants", "{\"name\":\"CP2-" + System.nanoTime() + "\"}");

        Map<String, Object> body = hearingBody("WEB-CAP", judge, room, Set.of(p),
                T, T.plusHours(1), 20, Set.of("RECORDING", "INTERPRETER"));
        mockMvc.perform(post("/api/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[?(@.reason=='CAPACITY')]").exists())
                .andExpect(jsonPath("$.conflicts[?(@.reason=='MISSING_FACILITY')].missingFacilities[0]")
                        .value("INTERPRETER"));
    }

    @Test
    void unknownResourceReturns404WithoutInternalLeak() throws Exception {
        mockMvc.perform(get("/api/hearings/NO-SUCH-CASE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/resources/judges")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unparseableTypeReturns400() throws Exception {
        long judge = register("/api/resources/judges", "{\"name\":\"TJ-" + System.nanoTime() + "\"}");
        mockMvc.perform(get("/api/hearings/schedule/WAT/" + judge)
                        .param("from", T.toString()).param("to", T.plusHours(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateResourceNameConflicts() throws Exception {
        String name = "DUP-JUDGE-" + System.nanoTime();
        register("/api/resources/judges", "{\"name\":\"" + name + "\"}");
        mockMvc.perform(post("/api/resources/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
    }

    @Test
    void unavailableWindowRejectsOverlappingHearing() throws Exception {
        long judge = register("/api/resources/judges", "{\"name\":\"UJ-" + System.nanoTime() + "\"}");
        long room = register("/api/resources/courtrooms",
                "{\"name\":\"UR-" + System.nanoTime() + "\",\"capacity\":10,\"facilities\":[]}");
        long p = register("/api/resources/participants", "{\"name\":\"UP-" + System.nanoTime() + "\"}");

        mockMvc.perform(post("/api/resources/COURTROOM/" + room + "/unavailable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startAt":"%s","endAt":"%s","reason":"维护"}"""
                                .formatted(T, T.plusHours(2))))
                .andExpect(status().isCreated());

        Map<String, Object> body = hearingBody("WEB-UNAV", judge, room, Set.of(p),
                T, T.plusHours(1), 5, Set.of());
        mockMvc.perform(post("/api/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[?(@.reason=='UNAVAILABLE')]").exists());
    }

    @Test
    void scheduleWindowRejectsInvertedRange() throws Exception {
        long judge = register("/api/resources/judges", "{\"name\":\"IJ2-" + System.nanoTime() + "\"}");
        mockMvc.perform(get("/api/hearings/schedule/JUDGE/" + judge)
                        .param("from", T.plusHours(1).toString())
                        .param("to", T.toString()))
                .andExpect(status().isBadRequest());
    }
}
