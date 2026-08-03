# 流域转移决策服务（basin-evacuation-decision）

面向四川盆地与重庆西部基层值班人员的转移决策服务。Java 21 + Spring Boot 3.5 + PostgreSQL，
数据库结构完全由 Flyway 管理（`spring.jpa.hibernate.ddl-auto=validate`，启动时只做校验、绝不重建表）。

## 领域模块

| 模块 | 包 | 职责 |
| --- | --- | --- |
| 行政区 | `region` | 行政区详情与检索 |
| 风险快照 | `snapshot` | 不可变快照（DB 触发器禁止 UPDATE/DELETE），记录四类上游健康状态 |
| 决策建议 | `decision` | 阈值评估器（唯一的决策规则所在地）、追加式建议历史、当前生效建议解析 |
| 人工覆写 | `override_` | 覆写必须含操作者/理由/过期时间；到期自动恢复计算结果；覆写永不删除 |
| 通知 | `notification` | 通知生成 + outbox（与决策同事务写入，失败可重放） |

控制器是薄层，不含任何决策规则；全部规则集中在 `decision/ThresholdEvaluator.java`。

## 决策结果

- 四个风险等级：`EVACUATE_NOW`（一级·立即转移，优先级 1）> `PRE_TRANSFER`（二级·预转移，2）
  > `PREPARE`（三级·准备，3）> `LOW_RISK`（四级·低风险，4）
- `INSUFFICIENT_DATA`（数据不足）**不是等级**（优先级 0）：核心证据（降水/水位/隐患点/主路）
  缺失或未知时返回，与「低风险」是两种完全不同的结果。
- 每条建议都携带 `snapshotId + snapshotVersion` 与完整证据 JSON（含四类上游版本），人口单位固定为「人」。

## 构建与测试

```bash
# 需要 JDK 21（gradle.properties 指向本机 Homebrew openjdk@21，可按需修改或删除该行）
./gradlew build        # 编译 + 全部测试（集成测试使用 Testcontainers，需要 Docker）
./gradlew test         # 仅测试
```

Testcontainers 说明：测试通过 `DOCKER_HOST` 找 Docker；OrbStack/新版 Docker 需要
`DOCKER_API_VERSION>=1.40`（build.gradle 已在 test 任务中默认处理，可用同名环境变量覆盖）。

## 启动

```bash
# 1) 准备 PostgreSQL（示例为本机 5432，账号/密码 postgres/postgres）
createdb evacuation || psql -c "CREATE DATABASE evacuation"

# 2) 启动（Flyway 自动执行 V1__init.sql 与 V2__seed_initial_data.sql；不会重建表）
./gradlew bootRun
# 或
java -jar build/libs/basin-evacuation-decision-1.0.0.jar --server.port=8081
```

数据源用 `spring.datasource.url/username/password` 覆盖。

- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- Swagger UI：`http://localhost:8080/swagger-ui.html`

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/regions` | 创建行政区 |
| GET | `/api/v1/regions/{code}` | 行政区详情 |
| GET | `/api/v1/regions?keyword=` | 行政区检索 |
| POST | `/api/v1/snapshots` | 创建不可变快照（同事务生成首条建议 + outbox） |
| GET | `/api/v1/snapshots/{id}` | 快照详情 |
| GET | `/api/v1/snapshots?regionCode=` | 快照检索 |
| POST | `/api/v1/snapshots/{id}/recompute` | 并发安全重算（行锁 + seq 唯一约束） |
| GET | `/api/v1/snapshots/{id}/decisions` | 建议历史（永不删除） |
| GET | `/api/v1/snapshots/{id}/current-decision` | 当前生效建议（未过期覆写优先） |
| GET | `/api/v1/decisions/{id}` | 建议详情（含证据版本） |
| GET | `/api/v1/decisions?regionCode&snapshotId&outcome&from&to` | 建议检索 |
| POST | `/api/v1/snapshots/{id}/overrides` | 人工覆写（operator/reason/expiresAt 必填） |
| GET | `/api/v1/snapshots/{id}/overrides` | 覆写列表（含已过期） |
| GET | `/api/v1/notifications?status=` | outbox 检索 |
| POST | `/api/v1/notifications/dispatch?limit=` | 投递/重放 PENDING 与 FAILED |

## 初始数据（V2/V3 迁移）

- `snapshot_id=sc-510182-20260729T0300`（V2），地区 510182（四川省成都市彭州市）：
  3h 降水 118mm、水位 6.12m、隐患点 WARNING、主路 CLOSED、次路 UNKNOWN、脆弱人群 286 人，
  四类上游健康状态均为 OK（版本批次 20260729T0300）；seq=1 一级·立即转移建议 + PENDING 通知。
- `snapshot_id=sc-510182-20260729T0600`（V3），同一地区：
  3h 降水 164mm、水位 6.18m、隐患点 WARNING、主路 OPEN、次路 UNKNOWN、脆弱人群 286 人，
  上游版本批次 20260729T0600；seq=1 一级·立即转移建议 + PENDING 通知（请求号 `seed-sc-510182-20260729T0600`）。

## 幂等（业务请求号）

创建快照（body `requestId`）、人工覆写（body `requestId`，必填）、重算（query `requestId`）
都接受业务请求号作为幂等依据：

- `decision.request_id` 与 `manual_override.request_id` 上有部分唯一索引；
- 写路径先持行锁（快照创建锁行政区分行，覆写/重算锁快照行），再按请求号查重 ——
  相同请求号的并发重放/重试返回既有建议，**不会生成新的 UUID、建议或 outbox**；
- 相同请求号但内容不一致：覆写返回 409；请求号被其它快照占用也返回 409；
- 快照创建时相同请求号重放返回 200（非 201），无请求号的重复创建返回 409。

每条通知 payload 都写明 `snapshotId`、`snapshotVersion`、四类上游版本（`upstreamVersions`）
与产生它的请求号（`requestId`）。

## 一致性保证

- 建议 + outbox 同一事务写入；覆写 = 覆写记录 + source=OVERRIDE 建议 + outbox，同一事务。
- 快照/建议/覆写由 DB 触发器禁止 UPDATE/DELETE；outbox 禁止 DELETE（只允许状态流转）。
- 并发重算：`SELECT ... FOR UPDATE` 锁住快照行串行化，`decision(snapshot_id, seq)` 唯一约束兜底。
- 覆写生效语义：`now < expiresAt` 生效，`now >= expiresAt` 过期并自动恢复最新计算结果（读时解析，无需清理任务）。
