-- =====================================================================
-- V4__snapshot_0600_and_override.sql
-- New Pengzhou snapshot at 06:00 (independent evidence version), plus the
-- idempotent manual override override-17 on the older 03:00 snapshot,
-- expiring 30 minutes after it was issued.
-- =====================================================================

INSERT INTO risk_snapshot (
    snapshot_id, district_code, observed_at,
    rainfall_3h_mm, water_level_m, hazard_status,
    main_road_status, secondary_road_status, vulnerable_population,
    rainfall_health, water_level_health, hazard_health, road_health,
    rainfall_version, water_level_version, hazard_version, road_version,
    evidence_version
) VALUES (
    'sc-510182-20260729T0600',
    '510182',
    '2026-07-29T06:00:00Z',
    164.0, 6.18, 'WARNING',
    'OPEN', 'UNKNOWN', 286,
    'HEALTHY', 'HEALTHY', 'HEALTHY', 'HEALTHY',
    'rain-v20260729.0600', 'wl-v20260729.0600', 'hz-v20260729.0600', 'rd-v20260729.0600',
    'sc-510182-20260729T0600'
);

INSERT INTO manual_override (
    snapshot_id, operator, reason, target_level, request_no, expires_at, created_at
) VALUES (
    'sc-510182-20260729T0300',
    '值班长-张三',
    '上游泥石流险情加剧，提前组织沿河群众转移',
    'RED',
    'override-17',
    '2026-07-29T03:30:00Z',
    '2026-07-29T03:00:00Z'
);
