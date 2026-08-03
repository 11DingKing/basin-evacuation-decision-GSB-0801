-- V3: Seed the administrative region and the initial immutable risk snapshot described in
-- the operational brief. The snapshot records 3h rainfall 118mm, river level 6.12m, hazard
-- point 'warning', primary road 'closed', secondary road 'unknown', 286 vulnerable persons,
-- plus the individual health of the four upstream feeds. No advisory is seeded: the service
-- computes it on demand / on the first recompute so evidence versioning stays authoritative.

INSERT INTO regions (region_code, name, parent_code)
VALUES ('510182', '四川省成都市都江堰市', NULL);

INSERT INTO risk_snapshots (
    snapshot_id, region_code, evidence_version, observed_at,
    rainfall_3h_mm, river_level_m,
    hazard_point_status, primary_road_status, secondary_road_status,
    vulnerable_population_persons
) VALUES (
    'sc-510182-20260729T0300', '510182', 1, TIMESTAMPTZ '2026-07-29 03:00:00+00',
    118.00, 6.12,
    'WARNING', 'CLOSED', 'UNKNOWN',
    286
);

INSERT INTO snapshot_upstream_health (snapshot_id, source, status, reported_version, observed_at) VALUES
    ('sc-510182-20260729T0300', 'RAINFALL',    'OK',    'rain-v1',  TIMESTAMPTZ '2026-07-29 03:00:00+00'),
    ('sc-510182-20260729T0300', 'RIVER_LEVEL', 'OK',    'river-v1', TIMESTAMPTZ '2026-07-29 03:00:00+00'),
    ('sc-510182-20260729T0300', 'HAZARD',      'OK',    'haz-v1',   TIMESTAMPTZ '2026-07-29 03:00:00+00'),
    ('sc-510182-20260729T0300', 'ROAD',        'OK',    'road-v1',  TIMESTAMPTZ '2026-07-29 03:00:00+00');

-- The seed used evidence_version 1 explicitly; advance the sequence so the next generated
-- version does not collide with it.
SELECT setval('evidence_version_seq', 1, true);
