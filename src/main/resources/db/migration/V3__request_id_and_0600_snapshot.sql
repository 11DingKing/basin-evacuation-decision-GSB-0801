-- ============================================================================
-- V3__request_id_and_0600_snapshot.sql
-- 1. 新增 0600 快照（四类上游版本同步更新到 20260729T0600）
-- 2. 为覆写/决策/通知增加 request_id 列，支撑幂等与全链路追踪
-- ============================================================================

INSERT INTO risk_snapshots(
    snapshot_id, region_code, observed_at,
    rainfall_3h_mm, water_level_m, hazard_status,
    primary_road_status, secondary_road_status, vulnerable_population,
    rainfall_health, water_level_health, hazard_health, infrastructure_health,
    rainfall_version, water_level_version, hazard_version, infrastructure_version
) VALUES (
    'sc-510182-20260729T0600',
    '510182',
    '2026-07-29T06:00:00Z',
    164.00, 6.18, 'WARNING',
    'OPEN', 'UNKNOWN', 286,
    'OK', 'OK', 'OK', 'OK',
    'rain-20260729T0600', 'wl-20260729T0600',
    'haz-20260729T0600', 'infra-20260729T0600'
);

-- request_id：业务请求号，用于幂等
ALTER TABLE manual_overrides
    ADD COLUMN request_id VARCHAR(64);

-- 同一 request_id 只能对应一条覆写（数据库层保证并发安全）
CREATE UNIQUE INDEX uk_manual_overrides_request_id
    ON manual_overrides(request_id)
    WHERE request_id IS NOT NULL;

-- 决策建议记录触发它的业务请求号（覆写请求或重算请求）
ALTER TABLE decision_advices
    ADD COLUMN request_id VARCHAR(64);

CREATE INDEX idx_advices_request ON decision_advices(request_id);

-- 通知 outbox 记录请求号，便于下游系统去重/追溯
ALTER TABLE notification_outbox
    ADD COLUMN request_id VARCHAR(64);

CREATE INDEX idx_outbox_request ON notification_outbox(request_id);

-- 覆写必须绑定到具体快照（针对哪一条证据快照做出的人工覆写）
ALTER TABLE manual_overrides
    ADD COLUMN snapshot_id VARCHAR(64) REFERENCES risk_snapshots(snapshot_id);

CREATE INDEX idx_overrides_snapshot ON manual_overrides(snapshot_id);
