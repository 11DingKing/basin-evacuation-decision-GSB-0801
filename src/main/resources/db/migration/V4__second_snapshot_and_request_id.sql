-- V4: Add a second immutable snapshot for 彭州市 and introduce an idempotency key
-- (business request number) on manual overrides.

-- The initial region row was seeded with a placeholder name; region 510182 is 彭州市.
UPDATE regions SET name = '四川省成都市彭州市' WHERE region_code = '510182';

-- Business request number for manual overrides. It is optional (older overrides have none),
-- but when present it must be globally unique so that replaying the same request is idempotent:
-- a partial UNIQUE index enforces "at most one override per request_id" at the database level,
-- which is what makes concurrent replay safe (the loser of the race hits this constraint).
ALTER TABLE manual_overrides ADD COLUMN request_id VARCHAR(64);
CREATE UNIQUE INDEX uq_override_request_id ON manual_overrides (request_id)
    WHERE request_id IS NOT NULL;

-- New immutable risk snapshot observed at 06:00. evidence_version is taken from the shared
-- sequence so it is strictly greater than the first snapshot's, keeping the two histories
-- independent yet globally ordered.
INSERT INTO risk_snapshots (
    snapshot_id, region_code, evidence_version, observed_at,
    rainfall_3h_mm, river_level_m,
    hazard_point_status, primary_road_status, secondary_road_status,
    vulnerable_population_persons
) VALUES (
    'sc-510182-20260729T0600', '510182', nextval('evidence_version_seq'),
    TIMESTAMPTZ '2026-07-29 06:00:00+00',
    164.00, 6.18,
    'WARNING', 'OPEN', 'UNKNOWN',
    286
);

-- All four upstream feeds refreshed to the 06:00 version.
INSERT INTO snapshot_upstream_health (snapshot_id, source, status, reported_version, observed_at) VALUES
    ('sc-510182-20260729T0600', 'RAINFALL',    'OK', '20260729T0600', TIMESTAMPTZ '2026-07-29 06:00:00+00'),
    ('sc-510182-20260729T0600', 'RIVER_LEVEL', 'OK', '20260729T0600', TIMESTAMPTZ '2026-07-29 06:00:00+00'),
    ('sc-510182-20260729T0600', 'HAZARD',      'OK', '20260729T0600', TIMESTAMPTZ '2026-07-29 06:00:00+00'),
    ('sc-510182-20260729T0600', 'ROAD',        'OK', '20260729T0600', TIMESTAMPTZ '2026-07-29 06:00:00+00');
