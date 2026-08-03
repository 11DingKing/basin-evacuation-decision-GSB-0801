-- V1: Core schema for the basin evacuation decision service.
-- Schema is owned entirely by Flyway; the application never rebuilds tables (ddl-auto=none).
-- Population unit is fixed to persons throughout (vulnerable_population_persons).

CREATE TABLE regions (
    region_code   VARCHAR(12)  PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    parent_code   VARCHAR(12)  REFERENCES regions (region_code),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Immutable risk snapshots. Each row is one versioned bundle of upstream evidence.
-- evidence_version is a monotonic, service-wide version number that advisories cite.
CREATE SEQUENCE evidence_version_seq START 1;

CREATE TABLE risk_snapshots (
    snapshot_id                   VARCHAR(64)  PRIMARY KEY,
    region_code                   VARCHAR(12)  NOT NULL REFERENCES regions (region_code),
    evidence_version              BIGINT       NOT NULL DEFAULT nextval('evidence_version_seq'),
    observed_at                   TIMESTAMPTZ  NOT NULL,
    rainfall_3h_mm                NUMERIC(6,2) NOT NULL,
    river_level_m                 NUMERIC(6,2) NOT NULL,
    hazard_point_status           VARCHAR(16)  NOT NULL,   -- normal | warning | danger | unknown
    primary_road_status           VARCHAR(16)  NOT NULL,   -- open | closed | unknown
    secondary_road_status         VARCHAR(16)  NOT NULL,   -- open | closed | unknown
    vulnerable_population_persons INTEGER      NOT NULL CHECK (vulnerable_population_persons >= 0),
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_snapshot_evidence_version UNIQUE (evidence_version)
);

CREATE INDEX idx_snapshots_region ON risk_snapshots (region_code, observed_at DESC);

-- Health of each of the four upstream feeds that produced the snapshot.
CREATE TABLE snapshot_upstream_health (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_id      VARCHAR(64)  NOT NULL REFERENCES risk_snapshots (snapshot_id),
    source           VARCHAR(24)  NOT NULL,   -- RAINFALL | RIVER_LEVEL | HAZARD | ROAD
    status           VARCHAR(16)  NOT NULL,   -- OK | TIMEOUT | STALE | ERROR
    reported_version VARCHAR(64),
    observed_at      TIMESTAMPTZ,
    CONSTRAINT uq_snapshot_source UNIQUE (snapshot_id, source)
);

-- Decision advisories. Append-only history: every recompute produces a new row.
-- decision_level is one of the four levels plus the distinct INSUFFICIENT_DATA outcome.
CREATE TABLE advisories (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_id            VARCHAR(64)  NOT NULL REFERENCES risk_snapshots (snapshot_id),
    region_code            VARCHAR(12)  NOT NULL REFERENCES regions (region_code),
    evidence_version       BIGINT       NOT NULL,   -- which snapshot evidence version this cites
    decision_level         VARCHAR(24)  NOT NULL,   -- INSUFFICIENT_DATA | LOW | WATCH | MOVE_PREPARE | MOVE_NOW
    priority               INTEGER      NOT NULL,   -- 0..4, higher = more urgent
    source                 VARCHAR(16)  NOT NULL,   -- COMPUTED | OVERRIDE
    override_id            BIGINT,                  -- set when source = OVERRIDE
    rationale              TEXT         NOT NULL,
    affected_persons       INTEGER      NOT NULL CHECK (affected_persons >= 0),
    computed_at            TIMESTAMPTZ  NOT NULL,
    superseded             BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_advisories_snapshot ON advisories (snapshot_id, computed_at DESC);
CREATE INDEX idx_advisories_region_current ON advisories (region_code) WHERE superseded = FALSE;

-- Manual overrides. Immutable + never deletable; expiry drives auto-revert to computed result.
CREATE TABLE manual_overrides (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_id       VARCHAR(64)  NOT NULL REFERENCES risk_snapshots (snapshot_id),
    region_code       VARCHAR(12)  NOT NULL REFERENCES regions (region_code),
    forced_level      VARCHAR(24)  NOT NULL,
    operator          VARCHAR(128) NOT NULL,
    reason            TEXT         NOT NULL,
    effective_from    TIMESTAMPTZ  NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_override_window CHECK (expires_at > effective_from)
);

CREATE INDEX idx_overrides_snapshot ON manual_overrides (snapshot_id, effective_from DESC);

ALTER TABLE advisories
    ADD CONSTRAINT fk_advisory_override FOREIGN KEY (override_id) REFERENCES manual_overrides (id);

-- Transactional outbox: notifications are written in the same transaction as the advisory,
-- then delivered asynchronously. Guarantees no advisory without a matching notification intent.
CREATE TABLE notification_outbox (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    advisory_id    BIGINT       NOT NULL REFERENCES advisories (id),
    snapshot_id    VARCHAR(64)  NOT NULL REFERENCES risk_snapshots (snapshot_id),
    evidence_version BIGINT     NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_pending ON notification_outbox (status, created_at) WHERE status <> 'SENT';
