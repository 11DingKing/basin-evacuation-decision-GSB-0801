-- V1: 流域转移决策服务数据库结构
-- 结构只通过 Flyway 演进，禁止应用启动时自动重建（spring.jpa.hibernate.ddl-auto=validate）

CREATE TABLE region (
    code        VARCHAR(12)  PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    parent_code VARCHAR(12),
    level       VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

-- 不可变风险快照：创建后不允许 UPDATE/DELETE（由触发器强制）
CREATE TABLE risk_snapshot (
    snapshot_id           VARCHAR(64) PRIMARY KEY,
    region_code           VARCHAR(12)  NOT NULL REFERENCES region (code),
    version               INTEGER      NOT NULL DEFAULT 1,
    rainfall_3h_mm        NUMERIC(6, 2),          -- 3 小时累计降水（mm），NULL 表示缺失
    water_level_m         NUMERIC(5, 3),          -- 河道水位（m），NULL 表示缺失
    hazard_point_status   VARCHAR(16)  NOT NULL,  -- OK / WARNING / CRITICAL / UNKNOWN
    primary_road_status   VARCHAR(16)  NOT NULL,  -- OPEN / CLOSED / UNKNOWN
    secondary_road_status VARCHAR(16)  NOT NULL,
    vulnerable_population INTEGER      NOT NULL CHECK (vulnerable_population >= 0), -- 单位：人
    upstream_health       JSONB        NOT NULL,  -- 四类上游：RAINFALL/WATER_LEVEL/HAZARD_POINT/ROAD
    observed_at           TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_snapshot_region ON risk_snapshot (region_code, created_at);

-- 人工覆写：必须含操作者、理由、过期时间；不可删除/修改
CREATE TABLE manual_override (
    id          UUID PRIMARY KEY,
    snapshot_id VARCHAR(64)  NOT NULL REFERENCES risk_snapshot (snapshot_id),
    outcome     VARCHAR(32)  NOT NULL,   -- 仅允许四个风险等级
    operator    VARCHAR(64)  NOT NULL,
    reason      VARCHAR(512) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_override_snapshot ON manual_override (snapshot_id, expires_at);

-- 决策建议：追加式历史，(snapshot_id, seq) 唯一，保证并发重算序号不重
CREATE TABLE decision (
    id               UUID PRIMARY KEY,
    snapshot_id      VARCHAR(64) NOT NULL REFERENCES risk_snapshot (snapshot_id),
    snapshot_version INTEGER     NOT NULL,           -- 引用的证据（快照）版本
    region_code      VARCHAR(12) NOT NULL,
    seq              INTEGER     NOT NULL,
    outcome          VARCHAR(32) NOT NULL,           -- EVACUATE_NOW/PRE_TRANSFER/PREPARE/LOW_RISK/INSUFFICIENT_DATA
    source           VARCHAR(16) NOT NULL,           -- COMPUTED / OVERRIDE
    override_id      UUID REFERENCES manual_override (id),
    reasons          JSONB       NOT NULL,           -- 触发规则与说明
    evidence         JSONB       NOT NULL,           -- 证据快照（输入数据 + 上游版本）
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_decision_snapshot_seq UNIQUE (snapshot_id, seq)
);
CREATE INDEX idx_decision_region ON decision (region_code, created_at);
CREATE INDEX idx_decision_outcome ON decision (outcome);
CREATE INDEX idx_decision_override ON decision (override_id);

-- 通知 outbox：与决策同事务写入，状态机 PENDING -> SENT / FAILED，可重放
CREATE TABLE notification_outbox (
    id          UUID PRIMARY KEY,
    decision_id UUID NOT NULL REFERENCES decision (id),
    channel     VARCHAR(32)  NOT NULL,
    payload     JSONB        NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    attempts    INTEGER      NOT NULL DEFAULT 0,
    last_error  VARCHAR(1024),
    created_at  TIMESTAMPTZ  NOT NULL,
    sent_at     TIMESTAMPTZ
);
CREATE INDEX idx_outbox_status ON notification_outbox (status, created_at);
CREATE INDEX idx_outbox_decision ON notification_outbox (decision_id);

-- 不可变约束：快照、决策、覆写禁止 UPDATE/DELETE；outbox 禁止 DELETE（允许更新投递状态）
CREATE OR REPLACE FUNCTION reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'table % is immutable: UPDATE and DELETE are forbidden', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_snapshot_immutable
    BEFORE UPDATE OR DELETE ON risk_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_decision_immutable
    BEFORE UPDATE OR DELETE ON decision
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_manual_override_immutable
    BEFORE UPDATE OR DELETE ON manual_override
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_outbox_no_delete
    BEFORE DELETE ON notification_outbox
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
