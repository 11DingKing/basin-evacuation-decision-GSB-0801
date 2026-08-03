-- =====================================================================
-- V1__init.sql : schema for the basin evacuation decision service
-- All tables use UTC timestamps. risk_snapshot / decision / manual_override
-- are append-only and immutable (see triggers at the bottom).
-- =====================================================================

CREATE TABLE district (
    code        VARCHAR(12) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    parent_code VARCHAR(12) REFERENCES district(code),
    level       VARCHAR(20)  NOT NULL CHECK (level IN ('PROVINCE','CITY','COUNTY','TOWN'))
);

-- Immutable risk evidence snapshot. One row = one frozen evidence bundle.
CREATE TABLE risk_snapshot (
    snapshot_id            VARCHAR(64) PRIMARY KEY,
    district_code          VARCHAR(12) NOT NULL REFERENCES district(code),
    observed_at            TIMESTAMPTZ NOT NULL,

    rainfall_3h_mm         NUMERIC(7,1),
    water_level_m          NUMERIC(7,2),
    hazard_status          VARCHAR(20) CHECK (hazard_status IN ('NORMAL','ATTENTION','WARNING','DANGER')),
    main_road_status       VARCHAR(20) CHECK (main_road_status IN ('OPEN','CONGESTED','CLOSED')),
    secondary_road_status  VARCHAR(20) CHECK (secondary_road_status IN ('OPEN','CONGESTED','CLOSED','UNKNOWN')),
    vulnerable_population  BIGINT      NOT NULL CHECK (vulnerable_population >= 0),

    -- Health of the four independent upstream feeds
    rainfall_health        VARCHAR(20) NOT NULL CHECK (rainfall_health       IN ('HEALTHY','STALE','TIMEOUT','UNAVAILABLE')),
    water_level_health     VARCHAR(20) NOT NULL CHECK (water_level_health    IN ('HEALTHY','STALE','TIMEOUT','UNAVAILABLE')),
    hazard_health          VARCHAR(20) NOT NULL CHECK (hazard_health         IN ('HEALTHY','STALE','TIMEOUT','UNAVAILABLE')),
    road_health            VARCHAR(20) NOT NULL CHECK (road_health           IN ('HEALTHY','STALE','TIMEOUT','UNAVAILABLE')),

    -- Upstream payload version per feed (used to detect stale / old versions)
    rainfall_version       VARCHAR(40),
    water_level_version    VARCHAR(40),
    hazard_version         VARCHAR(40),
    road_version           VARCHAR(40),

    evidence_version       VARCHAR(100) NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_snapshot_district ON risk_snapshot(district_code, observed_at DESC);

-- Append-only manual overrides. An override is "active" only while it is the
-- latest for a snapshot AND expires_at > now(). Expiry is evaluated at read /
-- recompute time; no row is ever updated or deleted.
CREATE TABLE manual_override (
    id           BIGSERIAL    PRIMARY KEY,
    snapshot_id  VARCHAR(64)  NOT NULL REFERENCES risk_snapshot(snapshot_id),
    operator     VARCHAR(100) NOT NULL,
    reason       TEXT         NOT NULL,
    target_level VARCHAR(30)  NOT NULL CHECK (target_level IN ('BLUE','YELLOW','ORANGE','RED')),
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_override_snapshot_active ON manual_override(snapshot_id, expires_at DESC, id DESC);

-- Append-only decision recommendations. Each row references exactly one
-- immutable evidence version (snapshot_id) and is never modified.
CREATE TABLE decision (
    id                 BIGSERIAL    PRIMARY KEY,
    snapshot_id        VARCHAR(64)  NOT NULL REFERENCES risk_snapshot(snapshot_id),
    district_code      VARCHAR(12)  NOT NULL,
    level              VARCHAR(30)  NOT NULL CHECK (level IN ('BLUE','YELLOW','ORANGE','RED','INSUFFICIENT_DATA')),
    computed_level     VARCHAR(30)  NOT NULL CHECK (computed_level IN ('BLUE','YELLOW','ORANGE','RED','INSUFFICIENT_DATA')),
    active_override_id BIGINT       REFERENCES manual_override(id),
    evidence_version   VARCHAR(100) NOT NULL,
    rationale          TEXT         NOT NULL,
    dimension_breakdown JSONB       NOT NULL,
    sequence_no        INTEGER      NOT NULL CHECK (sequence_no > 0),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_decision_snapshot_sequence UNIQUE (snapshot_id, sequence_no)
);

CREATE INDEX idx_decision_snapshot_latest ON decision(snapshot_id, sequence_no DESC);
CREATE INDEX idx_decision_district_created ON decision(district_code, created_at DESC);
CREATE INDEX idx_decision_level ON decision(level);

-- Transactional outbox. Written in the SAME local transaction as the decision.
-- A relay process marks rows SENT/FAILED; rows are never deleted.
CREATE TABLE notification_outbox (
    id              BIGSERIAL    PRIMARY KEY,
    snapshot_id     VARCHAR(64)  NOT NULL,
    decision_id     BIGINT       NOT NULL REFERENCES decision(id),
    channel         VARCHAR(30)  NOT NULL CHECK (channel IN ('SMS','VOICE','BROADCAST','PLATFORM')),
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    next_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_outbox_dispatch ON notification_outbox(status, next_attempt_at NULLS FIRST, created_at);

-- =====================================================================
-- Immutability / append-only enforcement
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table %.% is append-only; % is not permitted',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_snapshot_immutable
BEFORE UPDATE OR DELETE ON risk_snapshot
FOR EACH ROW EXECUTE FUNCTION fn_block_mutation();

CREATE TRIGGER trg_decision_immutable
BEFORE UPDATE OR DELETE ON decision
FOR EACH ROW EXECUTE FUNCTION fn_block_mutation();

CREATE TRIGGER trg_manual_override_immutable
BEFORE UPDATE OR DELETE ON manual_override
FOR EACH ROW EXECUTE FUNCTION fn_block_mutation();

CREATE TRIGGER trg_notification_outbox_no_delete
BEFORE DELETE ON notification_outbox
FOR EACH ROW EXECUTE FUNCTION fn_block_mutation();
