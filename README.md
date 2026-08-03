# 流域转移决策服务 (Basin Evacuation Decision Service)

面向四川盆地与重庆西部基层值班人员的流域转移决策服务。系统依据**降雨、河道水位、隐患点、道路状态、脆弱人群**，
在上游数据经常不齐、人工临时调整可能过期的现实约束下，快速给出**四级转移建议**，并保证决策、证据快照与通知的
可审计、可重放。

- **Java 21 + Spring Boot 3.5**
- **PostgreSQL**，schema 完全由 **Flyway** 管理（`ddl-auto=none`，启动**不**自动重建表）
- 领域分层：`阈值评估` / `人工覆写` / `通知生成` 为三个独立模块；控制器**不编写任何决策规则**
- 人口单位固定为**人**（`vulnerable_population_persons` / `affected_persons`，DTO 字段 `populationUnit=persons`）
- 每条建议都标注引用的**证据版本**（`evidenceVersion`）

---

## 1. 目录结构

```
src/main/java/gov/basin/evac
├── api/                     REST 控制器 + DTO（薄层，无决策规则）
├── application/             编排：RecomputeService / SnapshotService / OverrideService / Outbox*
├── config/                  可注入 Clock、阈值配置、OpenAPI
└── domain/
    ├── entity/              Region / RiskSnapshot(不可变) / Advisory / ManualOverride / NotificationOutbox
    ├── model/               枚举（含 INSUFFICIENT_DATA 与四级）
    ├── threshold/           【独立模块】ThresholdEvaluator —— 决策规则唯一归属
    ├── override/            【独立模块】OverrideResolver —— 生效窗口判定
    └── notification/        【独立模块】NotificationComposer / Sender —— 报文生成与投递
src/main/resources/db/migration
├── V1__core_schema.sql          建表 + 序列 + 索引
├── V2__immutability_guards.sql  数据库级不可变/不可删除触发器
└── V3__seed_initial_snapshot.sql 初始行政区与初始快照 sc-510182-20260729T0300
└── V4__second_snapshot_and_request_id.sql 第二个快照 sc-510182-20260729T0600 + 覆写请求号(幂等键)
```

---

## 2. 使用 Gradle 原生构建 / 启动

> 项目通过 Gradle **Java toolchain** 声明 Java 21。即使本机只装了 JDK 17，
> Gradle 也会借助 foojay resolver **自动下载 JDK 21** 完成编译与测试，无需手动切换 JDK。

所有命令使用随仓库附带的 **Gradle Wrapper**（`./gradlew`），无需预装 Gradle。

### 2.1 编译

```bash
./gradlew build
```

### 2.2 运行测试（需要 Docker：Testcontainers 会拉起真实 PostgreSQL）

```bash
./gradlew test
```

### 2.3 本地启动

服务需要一个 PostgreSQL。最简单的方式：

```bash
# 1) 起一个 PostgreSQL
docker run -d --name basin-pg \
  -e POSTGRES_DB=basin_evac -e POSTGRES_USER=basin -e POSTGRES_PASSWORD=basin \
  -p 5432:5432 postgres:16-alpine

# 2) 用 Gradle 原生方式启动（bootRun 会使用 toolchain 的 JDK 21）
./gradlew bootRun
```

默认连接 `jdbc:postgresql://localhost:5432/basin_evac`（用户/密码 `basin`/`basin`）。
可用环境变量覆盖：`DB_URL`、`DB_USER`、`DB_PASSWORD`、`SERVER_PORT`。

启动时 Flyway 会自动应用 `V1~V3` 迁移；**不会**由 Hibernate 重建任何表。

### 2.4 打成可执行 Jar 并运行

```bash
./gradlew bootJar
# 产物：build/libs/basin-evacuation-decision-1.0.0.jar
DB_URL=jdbc:postgresql://localhost:5432/basin_evac DB_USER=basin DB_PASSWORD=basin \
  java -jar build/libs/basin-evacuation-decision-1.0.0.jar
```

> 若命令行默认 `java` 不是 21，请用 JDK 21 运行该 jar（Gradle toolchain 只影响构建，不影响 `java -jar`）。

---

## 3. API 一览（OpenAPI / Swagger UI）

启动后：

- OpenAPI JSON：`GET /v3/api-docs`
- Swagger UI：`/swagger-ui.html`

| 方法 & 路径 | 说明 |
|---|---|
| `GET /api/regions` / `GET /api/regions/{code}` / `POST /api/regions` | 行政区管理 |
| `POST /api/snapshots` | 摄入不可变风险快照（含四类上游健康），并计算首个建议 |
| `GET /api/snapshots/{id}` | 快照详情 + 四类上游健康 |
| `POST /api/snapshots/{id}/recompute` | 重算建议（幂等、覆写感知） |
| `GET /api/snapshots/{id}/advisory` | 该快照当前建议 |
| `GET /api/snapshots/{id}/advisories` | 该快照建议历史（只追加，永不删除） |
| `POST /api/snapshots/{id}/overrides` | 新建不可变人工覆写（operator/reason/expiresAt 必填） |
| `GET /api/snapshots/{id}/overrides` | 覆写历史（永不删除） |
| `GET /api/advisories/{id}` | 建议详情（标注证据版本） |
| `GET /api/advisories?regionCode=&level=&currentOnly=` | 建议检索 |

---

## 4. 决策等级：输入证据、优先级与边界测试

阈值可在 `application.yml` 的 `basin.decision` 下调整。默认阶梯：
降雨(3h,mm) `watch=50 / move-prepare=80 / move-now=100`；水位(m) `watch=4.0 / move-prepare=5.5 / move-now=6.0`；
上游证据新鲜度上限 `30 分钟`。综合等级取各信号中**最严重**者。

| 决策等级 | 优先级 | 触发的输入证据 | 至少一个边界测试 |
|---|---|---|---|
| **INSUFFICIENT_DATA**（数据不足，**≠ 低风险**） | 1 | 任一**必需**上游（RAINFALL/RIVER_LEVEL）为 TIMEOUT/STALE/ERROR、缺失、或证据年龄 > 30min | `insufficientDataWhenRequiredFeedTimesOut`、`insufficientDataWhenRequiredFeedStale`、`insufficientDataBoundaryAtEvidenceAgeExpiry`（年龄=30min 仍新鲜→非数据不足；30min+1s→数据不足）；集成：`twoUpstreamTimeoutsAndOneStaleProduceInsufficientDataNotLow` |
| **LOW**（低风险） | 0 | 证据**齐全且新鲜**，且各信号均处于良性区间（降雨<50、水位<4.0、隐患 NORMAL、道路 OPEN/OPEN） | `lowWhenEvidenceCompleteAndBenign`、`watchJustBelowRainfallBoundaryStaysLow`（降雨 49.99→LOW） |
| **WATCH**（关注） | 2 | 降雨∈[50,80) 或 水位∈[4.0,5.5)，或隐患 UNKNOWN，或主路 UNKNOWN/次路 CLOSED | `watchAtRainfallLowerBoundary`（降雨=50→WATCH） |
| **MOVE_PREPARE**（准备转移） | 3 | 降雨∈[80,100) 或 水位∈[5.5,6.0)，或隐患 WARNING，或主路 CLOSED（次路非 CLOSED） | `movePrepareAtRiverLevelBoundary`（水位=5.5→MOVE_PREPARE） |
| **MOVE_NOW**（立即转移） | 4 | 降雨≥100 或 水位≥6.0，或隐患 DANGER，或主路+次路均 CLOSED | `moveNowAtRiverLevelBoundary`（水位=6.0→MOVE_NOW）；`takesMostSevereAcrossSignals`（两路皆断→MOVE_NOW）；`seedSnapshotEvaluatesToMoveNow` |

> 初始快照 `sc-510182-20260729T0300`（降雨 118、水位 6.12、隐患 warning、主路 closed、次路 unknown、脆弱人群 286 人）
> 在证据新鲜时评估为 **MOVE_NOW**（水位与主/次路证据同时触顶）。
>
> 第二个快照 `sc-510182-20260729T0600`（彭州市，降雨 164、水位 6.18、隐患 WARNING、主路 OPEN、次路 UNKNOWN、286 人，
> 四类上游版本 `20260729T0600`）在证据新鲜时评估为 **MOVE_NOW**（降雨与水位同时触顶，即使主路 OPEN）。
> 两个快照的证据与决策历史相互独立（`evidence_version` 各为 1、2）。

---

## 5. 幂等人工覆写（业务请求号）

- 覆写请求可携带**业务请求号** `requestId`（如 `override-17`）。
- 同一 `requestId` **幂等**：重复提交（含**并发重放**）只会产生**一条**覆写、**一条**覆写来源建议、**一条** outbox 记录，
  返回的都是同一条覆写。
- 实现两层保证：
  1. 服务先按 `requestId` 查已存在的覆写（常见路径直接返回）；
  2. 数据库 `manual_overrides(request_id)` **部分唯一索引**兜底并发竞态——竞争失败的事务（`REQUIRES_NEW` 隔离，
     见 `OverridePersister`）回滚后**重新读取**胜者的覆写。
  随后的 `recompute` 本身幂等（快照行级锁 + 结果比对），因此重放坍缩为同一条建议/outbox。

---

## 6. 关键不变量与可重放保证

- **不可变风险快照**：入库后不可 UPDATE/DELETE（`V2` 数据库触发器强制）；`evidence_version` 由序列生成，建议引用它。
- **四级 + 数据不足**：`INSUFFICIENT_DATA` 与 `LOW` 是两种完全不同的结果，缺失/过期证据绝不降级为“低风险”。
- **人工覆写**：必含 operator/reason/expiresAt；生效窗口为半开区间 `[effectiveFrom, expiresAt)`；
  **到期瞬间即失效**并自动回落到计算结果。覆写与建议历史**永不删除**（追加式）。
- **可注入时钟**：`Clock` 为 Spring bean，测试用 `MutableClock` 冻结在覆写**生效前 / 恰好到期 / 到期后一瞬间**。
- **并发重算**：对同一快照的重算通过 `SELECT ... FOR UPDATE` 行级锁串行化，最终**有且仅有一条**当前建议。
- **事务性 outbox**：建议与其通知记录在**同一事务**写入，绝不出现“看似成功、实际半写”的状态；
  投递失败仅将该 outbox 置为 `FAILED`（可重放），**不回滚**已生成的建议；重跑 `OutboxDispatcher` 即完成重放，不重复、不丢失。

---

## 7. 测试矩阵（`src/test`）

- **单元**（`ThresholdEvaluatorTest`）：四级 + 数据不足，每级含边界用例。
- **集成**（`RecomputeServiceIntegrationTest`，Testcontainers 真实 PostgreSQL）：
  - `overrideActiveBeforeExactlyAtAndAfterExpiry` —— 时钟冻结在覆写生效前/恰好到期/到期后一瞬间；
  - `concurrentRecomputeOfSameSnapshotYieldsExactlyOneCurrentAdvisory` —— 8 线程并发重算同一快照；
  - `twoUpstreamTimeoutsAndOneStaleProduceInsufficientDataNotLow` —— 两类超时 + 一类旧版本 ⇒ 数据不足（非低风险）；
  - `notificationSendFailureLeavesReplayableOutboxThenSucceedsOnRetry` —— 通知发送失败后 outbox 可重放并最终成功，建议不回滚；
  - `everyAdvisoryHasMatchingOutboxRecord_noHalfWrittenState` —— 每条建议都有对应 outbox 记录，无半写状态。
- **集成**（`SecondSnapshotOverrideIntegrationTest`，真实 PostgreSQL）：
  - `newSnapshotComputesMoveNowFromRainfallAndRiver` —— 第二个快照的独立评估；
  - `twoSnapshotsKeepIndependentEvidenceAndDecisionHistories` —— 两快照证据与决策历史相互独立；
  - `requestIdOverrideIsIdempotentOnSequentialReplay` —— 同请求号顺序重放不产生重复；
  - `requestIdOverrideIsIdempotentUnderConcurrentReplay` —— 8 线程并发同请求号，仅一条覆写/建议/outbox；
  - `notificationCarriesSnapshotVersionUpstreamVersionsAndRequestId` —— 通知含快照 ID、版本、上游版本、请求号。
