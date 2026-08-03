-- =====================================================================
-- V2__initial_data.sql : administrative hierarchy + the first immutable
-- risk snapshot for Pengzhou (510182).
-- Population unit is fixed to "person" (vulnerable_population column).
-- =====================================================================

INSERT INTO district (code, name, parent_code, level) VALUES
    ('510000', '四川省',     NULL,      'PROVINCE'),
    ('510100', '成都市',     '510000',  'CITY'),
    ('510182', '彭州市',     '510100',  'COUNTY');

INSERT INTO risk_snapshot (
    snapshot_id, district_code, observed_at,
    rainfall_3h_mm, water_level_m, hazard_status,
    main_road_status, secondary_road_status, vulnerable_population,
    rainfall_health, water_level_health, hazard_health, road_health,
    rainfall_version, water_level_version, hazard_version, road_version,
    evidence_version
) VALUES (
    'sc-510182-20260729T0300',
    '510182',
    '2026-07-29T03:00:00Z',
    118.0, 6.12, 'WARNING',
    'CLOSED', 'UNKNOWN', 286,
    'HEALTHY', 'HEALTHY', 'HEALTHY', 'HEALTHY',
    'rain-v20260729.0300', 'wl-v20260729.0300', 'hz-v20260729.0300', 'rd-v20260729.0300',
    'sc-510182-20260729T0300'
);
