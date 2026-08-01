-- V2: 种子数据 —— 四川盆地/重庆西部行政区与初始风险快照
-- 初始快照：snapshot_id=sc-510182-20260729T0300，地区 510182（四川省成都市彭州市）
-- 3h 累计降水 118mm，河道水位 6.12m，隐患点 warning，主路 closed，次路 unknown，脆弱人群 286 人

INSERT INTO region (code, name, parent_code, level, created_at) VALUES
    ('510000', '四川省', NULL,     'PROVINCE',   '2026-07-29T03:00:00+08:00'),
    ('510100', '成都市', '510000', 'PREFECTURE', '2026-07-29T03:00:00+08:00'),
    ('510182', '彭州市', '510100', 'COUNTY',     '2026-07-29T03:00:00+08:00'),
    ('500000', '重庆市', NULL,     'PROVINCE',   '2026-07-29T03:00:00+08:00'),
    ('500116', '江津区', '500000', 'COUNTY',     '2026-07-29T03:00:00+08:00');

-- 四类上游健康状态（本次全部正常，各自携带数据版本）
INSERT INTO risk_snapshot (
    snapshot_id, region_code, version,
    rainfall_3h_mm, water_level_m,
    hazard_point_status, primary_road_status, secondary_road_status,
    vulnerable_population, upstream_health, observed_at, created_at
) VALUES (
    'sc-510182-20260729T0300', '510182', 1,
    118.00, 6.120,
    'WARNING', 'CLOSED', 'UNKNOWN',
    286,
    '{
      "RAINFALL":     {"status": "OK", "version": "rain-20260729T0300",  "detail": null},
      "WATER_LEVEL":  {"status": "OK", "version": "wl-20260729T0300",    "detail": null},
      "HAZARD_POINT": {"status": "OK", "version": "hz-20260729T0300",    "detail": null},
      "ROAD":         {"status": "OK", "version": "road-20260729T0300", "detail": null}
    }'::jsonb,
    '2026-07-29T03:00:00+08:00',
    '2026-07-29T03:00:00+08:00'
);

-- 初始计算建议（seq=1）：与 ThresholdEvaluator 输出保持一致
-- 118mm >= 100mm 且 6.12m >= 6.0m -> 一级·立即转移
INSERT INTO decision (
    id, snapshot_id, snapshot_version, region_code, seq,
    outcome, source, override_id, reasons, evidence, created_at
) VALUES (
    'a1000000-0000-4000-8000-000000000001',
    'sc-510182-20260729T0300', 1, '510182', 1,
    'EVACUATE_NOW', 'COMPUTED', NULL,
    '[
      "达到一级（立即转移）阈值：3小时累计降水118mm≥100mm且河道水位6.12m≥6m",
      "主路封闭：撤离路线须避开主路",
      "次路状态未知：需人工核实备用路线",
      "脆弱人群286人：须优先安排转运"
    ]'::jsonb,
    '{
      "snapshotId": "sc-510182-20260729T0300",
      "snapshotVersion": 1,
      "regionCode": "510182",
      "rainfall3hMm": 118.00,
      "waterLevelM": 6.120,
      "hazardPointStatus": "WARNING",
      "primaryRoadStatus": "CLOSED",
      "secondaryRoadStatus": "UNKNOWN",
      "vulnerablePopulation": 286,
      "upstreamHealth": {
        "RAINFALL":     {"status": "OK", "version": "rain-20260729T0300",  "detail": null},
        "WATER_LEVEL":  {"status": "OK", "version": "wl-20260729T0300",    "detail": null},
        "HAZARD_POINT": {"status": "OK", "version": "hz-20260729T0300",    "detail": null},
        "ROAD":         {"status": "OK", "version": "road-20260729T0300", "detail": null}
      }
    }'::jsonb,
    '2026-07-29T03:00:00+08:00'
);

-- 初始建议对应的通知 outbox（PENDING，等待投递，可重放）
INSERT INTO notification_outbox (
    id, decision_id, channel, payload, status, attempts, last_error, created_at, sent_at
) VALUES (
    'b1000000-0000-4000-8000-000000000001',
    'a1000000-0000-4000-8000-000000000001',
    'DUTY_BROADCAST',
    '{
      "title": "[一级·立即转移] 510182 流域转移建议",
      "body": "达到一级（立即转移）阈值：3小时累计降水118mm≥100mm且河道水位6.12m≥6m；主路封闭：撤离路线须避开主路；次路状态未知：需人工核实备用路线；脆弱人群286人：须优先安排转运",
      "decisionId": "a1000000-0000-4000-8000-000000000001",
      "snapshotId": "sc-510182-20260729T0300",
      "snapshotVersion": 1,
      "outcome": "EVACUATE_NOW",
      "regionCode": "510182",
      "vulnerablePopulation": 286
    }'::jsonb,
    'PENDING', 0, NULL,
    '2026-07-29T03:00:00+08:00', NULL
);
