# cc-court-calendar

庭审排期管理服务：登记法官、法庭与参与人，对案件庭审进行**同时占用法官、法庭和多名参与人**的排期，
支持冲突校验、并发安全的原子排期、幂等重放与不丢失原档期的原子改期。

## 技术栈

- JDK 21、Spring Boot 4.1.1（Web MVC、Data JPA、Validation）
- JPA / Hibernate、H2 数据库
- JUnit 5 + MockMvc 自动化测试

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 主要业务规则

1. **资源登记**：可登记法官、法庭、参与人，各自维护若干**不可用时间段**；法庭额外包含
   容量和设施集合。庭审记录案件号（全局唯一）、预计人数、所需设施、一名法官及多名参与人。
2. **时间语义**：所有时间区间采用**左闭右开** `[start, end)`，因此两场时间端点相接
   （如 10:00 结束、10:00 开始）的排期不算冲突。
3. **排期约束（必须同时全部满足）**：
   - 法官、法庭以及**每一名**参与人在该时间段均无其他庭审；
   - 时间不落入法官、法庭或任何参与人的不可用时段；
   - 法庭容量 ≥ 庭审预计人数；
   - 法庭设施集合包含庭审所需的全部设施（设施名忽略大小写）；
   - 同一法官相邻两场庭审之间必须留出缓冲时间，默认 **15 分钟**
     （可通过 `courtcalendar.judge-buffer` 配置，如 `PT30M`）。
4. **原子生效与并发安全**：一次排期对全部资源原子生效。排期/改期事务先对一张单行
   互斥表执行 `SELECT … FOR UPDATE`，使所有写事务在数据库层串行化，两个并发请求争抢
   任一资源时**绝不会形成双重占用**（一个成功，另一个得到明确的资源冲突）。
5. **幂等**：排期和改期可携带 `Idempotency-Key` 请求头。相同幂等键 + 相同请求内容重放时
   返回首次的原结果（不重复占用）；相同幂等键但内容不同返回 `409 IDEMPOTENCY_CONFLICT`。
6. **原子改期**：改期时保留原排期记录，在新时间上做完整校验与锁定；只有新档期能够完整
   成立时才一次性切换到新时间。任何校验失败都会回滚，**原时间保持不变**。
7. **版本控制**：庭审详情包含 `version`。改期请求必须携带读取到的版本号；基于过期状态的
   更新（版本号不匹配）返回 `409 STALE_VERSION`，并发改期恰好生效一次。
8. **查询**：可按案件号查询排期详情，也可按资源（`judge` / `courtroom` / `participant`）
   查询日程，结果按开始时间、案件号稳定排序。
9. **错误响应**：冲突响应逐项列出冲突资源（类型、名称、原因、冲突案件号），但不向客户端
   泄露堆栈、SQL 等内部异常信息。

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/resources/judges` | 登记法官（可带不可用时段） |
| POST | `/api/resources/courtrooms` | 登记法庭（容量、设施、不可用时段） |
| POST | `/api/resources/participants` | 登记参与人 |
| GET | `/api/resources/judges` 等 | 查询资源列表/详情 |
| POST | `/api/resources/{judges,courtrooms,participants}/{id}/unavailability` | 追加不可用时段 |
| POST | `/api/hearings` | 创建排期（可带 `Idempotency-Key`） |
| PUT | `/api/hearings/{caseNumber}/reschedule` | 原子改期（body 携带 `version`） |
| GET | `/api/hearings/{caseNumber}` | 案件排期详情 |
| GET | `/api/hearings?resourceType=judge&resourceId=1` | 按资源查询日程 |

### 排期请求示例

```json
POST /api/hearings
Idempotency-Key: 7e1f...
Content-Type: application/json

{
  "caseNumber": "(2026)京01民初123号",
  "expectedAttendees": 12,
  "requiredFacilities": ["录音", "显示屏"],
  "judgeId": 1,
  "courtroomId": 1,
  "participantIds": [1, 2, 3],
  "start": "2026-10-01T09:00:00",
  "end": "2026-10-01T10:00:00"
}
```

### 改期请求示例

```json
PUT /api/hearings/(2026)京01民初123号/reschedule

{
  "version": 0,
  "start": "2026-10-03T14:00:00",
  "end": "2026-10-03T15:00:00"
}
```

### 冲突响应示例（HTTP 409）

```json
{
  "timestamp": "2026-10-01T09:00:00",
  "status": 409,
  "error": "SCHEDULING_CONFLICT",
  "message": "排期不满足约束，存在 2 项资源冲突",
  "path": "/api/hearings",
  "conflicts": [
    {
      "resourceType": "judge",
      "resourceId": 1,
      "resourceName": "张法官",
      "reason": "OCCUPIED",
      "conflictingCase": "(2026)京01民初100号",
      "detail": "法官在该时间段已有庭审: (2026)京01民初100号"
    },
    {
      "resourceType": "courtroom",
      "resourceId": 1,
      "resourceName": "第一法庭",
      "reason": "FACILITY",
      "conflictingCase": null,
      "detail": "法庭缺少所需设施: 显示屏"
    }
  ]
}
```

冲突原因取值：`OCCUPIED`（时间被占用）、`UNAVAILABLE`（不可用时段）、
`BUFFER`（法官缓冲不足）、`CAPACITY`（容量不足）、`FACILITY`（设施缺失）。
