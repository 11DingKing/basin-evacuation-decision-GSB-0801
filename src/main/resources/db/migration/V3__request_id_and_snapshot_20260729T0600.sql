-- V3: 幂等业务请求号 + 新快照 sc-510182-20260729T0600
-- request_id 是幂等依据：同一请求号只产生一条建议与一条通知（部分唯一索引，历史 NULL 不受影响）

ALTER TABLE decision ADD COLUMN request_id VARCHAR(64);
CREATE UNIQUE INDEX uq_decision_request_id ON decision (request_id) WHERE request_id IS NOT NULL;

ALTER TABLE manual_override ADD COLUMN request_id VARCHAR(64);
CREATE UNIQUE INDEX uq_override_request_id ON manual_override (request_id) WHERE request_id IS NOT NULL;

-- 新快照：彭州市 2026-07-29 06:00（+08:00）
-- 3h 累计降水 164mm，河道水位 6.18m，隐患点 WARNING，主路 OPEN，次路 UNKNOWN，脆弱人群 286 人
-- 四类上游版本切换为 20260729T0600 批次
INSERT INTO risk_snapshot (
    snapshot_id, region_code, version,
    rainfall_3h_mm, water_level_m,
    hazard_point_status, primary_road_status, secondary_road_status,
    vulnerable_population, upstream_health, observed_at, created_at
) VALUES (
    'sc-510182-20260729T0600', '510182', 1,
    164.00, 6.180,
    'WARNING', 'OPEN', 'UNKNOWN',
    286,
    '{
      "RAINFALL":     {"status": "OK", "version": "rain-20260729T0600",  "detail": null},
      "WATER_LEVEL":  {"status": "OK", "version": "wl-20260729T0600",    "detail": null},
      "HAZARD_POINT": {"status": "OK", "version": "hz-20260729T0600",    "detail": null},
      "ROAD":         {"status": "OK", "version": "road-20260729T0600", "detail": null}
    }'::jsonb,
    '2026-07-29T06:00:00+08:00',
    '2026-07-29T06:00:00+08:00'
);

-- 新快照的首条计算建议（seq=1）：164mm >= 100mm 且 6.18m >= 6.0m -> 一级·立即转移
INSERT INTO decision (
    id, snapshot_id, snapshot_version, region_code, seq,
    outcome, source, override_id, request_id, reasons, evidence, created_at
) VALUES (
    'a1000000-0000-4000-8000-000000000002',
    'sc-510182-20260729T0600', 1, '510182', 1,
    'EVACUATE_NOW', 'COMPUTED', NULL, 'seed-sc-510182-20260729T0600',
    '[
      "达到一级（立即转移）阈值：3小时累计降水164mm≥100mm且河道水位6.18m≥6m",
      "次路状态未知：需人工核实备用路线",
      "脆弱人群286人：须优先安排转运"
    ]'::jsonb,
    '{
      "snapshotId": "sc-510182-20260729T0600",
      "snapshotVersion": 1,
      "regionCode": "510182",
      "rainfall3hMm": 164.00,
      "waterLevelM": 6.180,
      "hazardPointStatus": "WARNING",
      "primaryRoadStatus": "OPEN",
      "secondaryRoadStatus": "UNKNOWN",
      "vulnerablePopulation": 286,
      "upstreamHealth": {
        "RAINFALL":     {"status": "OK", "version": "rain-20260729T0600",  "detail": null},
        "WATER_LEVEL":  {"status": "OK", "version": "wl-20260729T0600",    "detail": null},
        "HAZARD_POINT": {"status": "OK", "version": "hz-20260729T0600",    "detail": null},
        "ROAD":         {"status": "OK", "version": "road-20260729T0600", "detail": null}
      }
    }'::jsonb,
    '2026-07-29T06:00:00+08:00'
);

-- 新快照建议对应的通知 outbox（payload 含 snapshotId、snapshotVersion、四类上游版本与请求号）
INSERT INTO notification_outbox (
    id, decision_id, channel, payload, status, attempts, last_error, created_at, sent_at
) VALUES (
    'b1000000-0000-4000-8000-000000000002',
    'a1000000-0000-4000-8000-000000000002',
    'DUTY_BROADCAST',
    '{
      "title": "[一级·立即转移] 510182 流域转移建议",
      "body": "达到一级（立即转移）阈值：3小时累计降水164mm≥100mm且河道水位6.18m≥6m；次路状态未知：需人工核实备用路线；脆弱人群286人：须优先安排转运",
      "decisionId": "a1000000-0000-4000-8000-000000000002",
      "snapshotId": "sc-510182-20260729T0600",
      "snapshotVersion": 1,
      "outcome": "EVACUATE_NOW",
      "regionCode": "510182",
      "vulnerablePopulation": 286,
      "requestId": "seed-sc-510182-20260729T0600",
      "upstreamVersions": {
        "RAINFALL": "rain-20260729T0600",
        "WATER_LEVEL": "wl-20260729T0600",
        "HAZARD_POINT": "hz-20260729T0600",
        "ROAD": "road-20260729T0600"
      }
    }'::jsonb,
    'PENDING', 0, NULL,
    '2026-07-29T06:00:00+08:00', NULL
);
