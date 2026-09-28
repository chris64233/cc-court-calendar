package com.chris64233.cc.courtcalendar.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 端到端 HTTP 测试：幂等头、冲突响应结构、改期版本与稳定排序、错误不泄露内部异常。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:db-http;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
class HearingApiHttpTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullFlowRegisterScheduleConflictQueryReschedule() throws Exception {
        // 登记法官
        mockMvc.perform(post("/api/resources/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"HTTP法官"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        // 登记法庭（容量与设施）
        mockMvc.perform(post("/api/resources/courtrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"HTTP法庭","capacity":10,"facilities":["录音","显示屏"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        // 登记两名参与人
        mockMvc.perform(post("/api/resources/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"HTTP参与人1"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/resources/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"HTTP参与人2"}
                                """))
                .andExpect(status().isCreated());

        String body = """
                {
                  "caseNumber": "HTTP-CASE-1",
                  "expectedAttendees": 8,
                  "requiredFacilities": ["录音"],
                  "judgeId": 1,
                  "courtroomId": 1,
                  "participantIds": [1, 2],
                  "start": "2027-01-10T09:00:00",
                  "end": "2027-01-10T10:00:00"
                }
                """;

        // 首次排期成功
        mockMvc.perform(post("/api/hearings")
                        .header("Idempotency-Key", "idem-http-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").value("HTTP-CASE-1"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.participants[0].name").value("HTTP参与人1"));

        // 同键同内容重放 → 返回原结果，不新建
        mockMvc.perform(post("/api/hearings")
                        .header("Idempotency-Key", "idem-http-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").value("HTTP-CASE-1"))
                .andExpect(jsonPath("$.version").value(0));

        // 同键不同内容 → 409 IDEMPOTENCY_CONFLICT
        mockMvc.perform(post("/api/hearings")
                        .header("Idempotency-Key", "idem-http-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("HTTP-CASE-1", "HTTP-CASE-OTHER")
                                .replace("09:00:00", "11:00:00")
                                .replace("10:00:00", "12:00:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.conflicts").doesNotExist());

        // 争抢同一时段 → 409，响应明确列出冲突资源
        mockMvc.perform(post("/api/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("HTTP-CASE-1", "HTTP-CASE-2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCHEDULING_CONFLICT"))
                .andExpect(jsonPath("$.conflicts[0].resourceType").exists())
                .andExpect(jsonPath("$.conflicts[?(@.resourceType=='judge')].reason")
                        .value(org.hamcrest.Matchers.hasItem("OCCUPIED")))
                .andExpect(jsonPath("$.conflicts[?(@.resourceType=='courtroom')].reason")
                        .value(org.hamcrest.Matchers.hasItem("OCCUPIED")))
                .andExpect(jsonPath("$.conflicts[?(@.resourceType=='participant')].reason")
                        .value(org.hamcrest.Matchers.hasItem("OCCUPIED")));

        // 容量/设施不足
        mockMvc.perform(post("/api/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("HTTP-CASE-1", "HTTP-CASE-3")
                                .replace("2027-01-10T09:00:00", "2027-01-11T09:00:00")
                                .replace("2027-01-10T10:00:00", "2027-01-11T10:00:00")
                                .replace("\"expectedAttendees\": 8", "\"expectedAttendees\": 50")
                                .replace("[\"录音\"]", "[\"录音\",\"法警通道\"]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[?(@.reason=='CAPACITY')]").isNotEmpty())
                .andExpect(jsonPath("$.conflicts[?(@.reason=='FACILITY')]").isNotEmpty());

        // 案件详情
        mockMvc.perform(get("/api/hearings/HTTP-CASE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2027-01-10T09:00:00"));

        // 按资源查询日程（稳定排序：先放一场更早的）
        mockMvc.perform(post("/api/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("HTTP-CASE-1", "HTTP-CASE-EARLY")
                                .replace("2027-01-10T09:00:00", "2027-01-09T08:00:00")
                                .replace("2027-01-10T10:00:00", "2027-01-09T09:00:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/hearings").param("resourceType", "judge").param("resourceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseNumber").value("HTTP-CASE-EARLY"))
                .andExpect(jsonPath("$[1].caseNumber").value("HTTP-CASE-1"));

        // 过期版本改期 → 409 STALE_VERSION
        mockMvc.perform(put("/api/hearings/HTTP-CASE-1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":99,"start":"2027-01-12T09:00:00","end":"2027-01-12T10:00:00"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("STALE_VERSION"));

        // 改到冲突时间失败 → 原档期保留
        mockMvc.perform(put("/api/hearings/HTTP-CASE-1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"start":"2027-01-09T08:30:00","end":"2027-01-09T09:30:00"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCHEDULING_CONFLICT"));

        mockMvc.perform(get("/api/hearings/HTTP-CASE-1"))
                .andExpect(jsonPath("$.start").value("2027-01-10T09:00:00"))
                .andExpect(jsonPath("$.version").value(0));

        // 合法改期成功 → 版本变 1，原档期释放（可在原时间排别的庭，缓冲间隔需 ≥15 分钟，
        // 改到 1 月 13 日完全不相邻）
        mockMvc.perform(put("/api/hearings/HTTP-CASE-1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"start":"2027-01-13T09:00:00","end":"2027-01-13T10:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.start").value("2027-01-13T09:00:00"));

        mockMvc.perform(get("/api/hearings/HTTP-CASE-1"))
                .andExpect(jsonPath("$.start").value("2027-01-13T09:00:00"));
    }

    @Test
    void errorsAreGenericAndDoNotLeakInternals() throws Exception {
        // 不存在的资源 → 404，结构统一
        mockMvc.perform(get("/api/hearings/NO-SUCH-CASE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").exists());

        // 参数非法 → 400
        mockMvc.perform(post("/api/resources/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        // 时间区间非法（开始不早于结束）→ 400
        mockMvc.perform(post("/api/resources/judges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"区间非法法官","unavailableRanges":[
                                  {"start":"2027-05-01T10:00:00","end":"2027-05-01T09:00:00"}
                                ]}
                                """))
                .andExpect(status().isBadRequest());
    }
}
