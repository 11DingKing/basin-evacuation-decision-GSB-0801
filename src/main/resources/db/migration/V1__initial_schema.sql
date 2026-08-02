-- ============================================================================
-- V1__initial_schema.sql
-- 流域转移决策服务 - 初始结构
-- 设计要点：
--   * risk_snapshots / decision_advices / manual_overrides 一旦写入不可更新/删除
--     通过触发器在数据库层强制
--   * notification_outbox 使用状态机 PENDING/SENT/FAILED，可重试
--   * 决策与证据快照通过 snapshot_id 强绑定
-- ============================================================================

CREATE TABLE regions (
    code        VARCHAR(12) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    parent_code VARCHAR(12),
    level       SMALLINT     NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_regions_parent ON regions(parent_code);

INSERT INTO regions(code, name, parent_code, level) VALUES
    ('510000', '四川省',     NULL, 1),
    ('500000', '重庆市',     NULL, 1),
    ('510100', '成都市',     '510000', 2),
    ('500100', '重庆市辖区', '500000', 2),
    ('510182', '彭州市',     '510100', 3);

-- ----------------------------------------------------------------------------
-- 不可变风险快照
-- ----------------------------------------------------------------------------
CREATE TABLE risk_snapshots (
    snapshot_id          VARCHAR(64) PRIMARY KEY,
    region_code          VARCHAR(12) NOT NULL REFERENCES regions(code),
    observed_at          TIMESTAMPTZ NOT NULL,
    rainfall_3h_mm       NUMERIC(7,2),
    water_level_m        NUMERIC(6,2),
    hazard_status        VARCHAR(16),
    primary_road_status  VARCHAR(16),
    secondary_road_status VARCHAR(16),
    vulnerable_population INTEGER,
    rainfall_health      VARCHAR(16) NOT NULL,
    water_level_health   VARCHAR(16) NOT NULL,
    hazard_health        VARCHAR(16) NOT NULL,
    infrastructure_health VARCHAR(16) NOT NULL,
    rainfall_version     VARCHAR(64),
    water_level_version  VARCHAR(64),
    hazard_version       VARCHAR(64),
    infrastructure_version VARCHAR(64),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_snapshots_region_time ON risk_snapshots(region_code, observed_at DESC);

-- 初始快照
INSERT INTO risk_snapshots(
    snapshot_id, region_code, observed_at,
    rainfall_3h_mm, water_level_m, hazard_status,
    primary_road_status, secondary_road_status, vulnerable_population,
    rainfall_health, water_level_health, hazard_health, infrastructure_health,
    rainfall_version, water_level_version, hazard_version, infrastructure_version
) VALUES (
    'sc-510182-20260729T0300',
    '510182',
    '2026-07-29T03:00:00Z',
    118.00, 6.12, 'WARNING',
    'CLOSED', 'UNKNOWN', 286,
    'OK', 'OK', 'OK', 'OK',
    'rain-v20260729.0300', 'wl-v20260729.0300',
    'haz-v20260729.0300', 'infra-v20260729.0300'
);

-- ----------------------------------------------------------------------------
-- 不可变决策建议
-- decision_level: LEVEL_1 / LEVEL_2 / LEVEL_3 / LEVEL_4 / INSUFFICIENT_DATA
-- source:         COMPUTED / OVERRIDDEN
-- evidence_ref:   JSONB，记录所引用的各上游证据版本
-- ----------------------------------------------------------------------------
CREATE TABLE decision_advices (
    id              BIGSERIAL PRIMARY KEY,
    snapshot_id     VARCHAR(64) NOT NULL REFERENCES risk_snapshots(snapshot_id),
    region_code     VARCHAR(12) NOT NULL,
    decision_level  VARCHAR(24) NOT NULL,
    source          VARCHAR(16) NOT NULL,
    advice_text     TEXT        NOT NULL,
    evidence_ref    JSONB       NOT NULL,
    override_id     BIGINT,
    computed_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_advices_snapshot ON decision_advices(snapshot_id, computed_at DESC);
CREATE INDEX idx_advices_region   ON decision_advices(region_code, computed_at DESC);
CREATE INDEX idx_advices_level    ON decision_advices(decision_level);

-- ----------------------------------------------------------------------------
-- 人工覆写（不可删除；覆写记录本身允许将 status 从 ACTIVE 改为 EXPIRED，
-- 但 operator / reason / expires_at / target_level 一经写入不可变）
-- ----------------------------------------------------------------------------
CREATE TABLE manual_overrides (
    id              BIGSERIAL PRIMARY KEY,
    region_code     VARCHAR(12) NOT NULL,
    operator        VARCHAR(100) NOT NULL,
    reason          TEXT        NOT NULL,
    target_level    VARCHAR(24) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from  TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_override_status CHECK (status IN ('ACTIVE','EXPIRED'))
);

CREATE INDEX idx_overrides_region_active
    ON manual_overrides(region_code, expires_at)
    WHERE status = 'ACTIVE';

-- ----------------------------------------------------------------------------
-- 通知 outbox：决策与通知在同一事务写入，真正发送由调度器异步完成
-- status: PENDING / SENT / FAILED
-- ----------------------------------------------------------------------------
CREATE TABLE notification_outbox (
    id              BIGSERIAL PRIMARY KEY,
    advice_id       BIGINT NOT NULL REFERENCES decision_advices(id),
    region_code     VARCHAR(12) NOT NULL,
    channel         VARCHAR(32) NOT NULL,
    payload         JSONB       NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER     NOT NULL DEFAULT 0,
    last_error      TEXT,
    next_retry_at   TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','SENT','FAILED'))
);

CREATE INDEX idx_outbox_due ON notification_outbox(next_retry_at, status)
    WHERE status IN ('PENDING','FAILED');

-- ----------------------------------------------------------------------------
-- 不可变性触发器：禁止 UPDATE/DELETE 关键表
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'table % is immutable; operation % is forbidden',
        TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_snapshots_immutable
    BEFORE UPDATE OR DELETE ON risk_snapshots
    FOR EACH ROW EXECUTE FUNCTION fn_immutable();

CREATE TRIGGER trg_decision_advices_immutable
    BEFORE UPDATE OR DELETE ON decision_advices
    FOR EACH ROW EXECUTE FUNCTION fn_immutable();

-- manual_overrides 的不可变字段由应用层保证；status 字段允许从 ACTIVE -> EXPIRED
CREATE OR REPLACE FUNCTION fn_override_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'manual_overrides rows cannot be deleted';
    END IF;
    IF NEW.operator       IS DISTINCT FROM OLD.operator
       OR NEW.reason     IS DISTINCT FROM OLD.reason
       OR NEW.target_level IS DISTINCT FROM OLD.target_level
       OR NEW.region_code IS DISTINCT FROM OLD.region_code
       OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'core columns of manual_overrides are immutable';
    END IF;
    IF OLD.status = 'EXPIRED' AND NEW.status = 'ACTIVE' THEN
        RAISE EXCEPTION 'expired override cannot be reactivated';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_override_guard
    BEFORE UPDATE OR DELETE ON manual_overrides
    FOR EACH ROW EXECUTE FUNCTION fn_override_guard();
