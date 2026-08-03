-- =====================================================================
-- V3__override_request_no.sql : idempotent manual overrides.
-- request_no is the external business request number. A unique partial
-- index guarantees that concurrent replays of the same request can only
-- ever produce one override row. NULL request_no remains allowed for
-- internally-created overrides (multiple NULLs do not conflict).
-- =====================================================================

ALTER TABLE manual_override
    ADD COLUMN request_no VARCHAR(64);

CREATE UNIQUE INDEX uq_override_request_no
    ON manual_override(request_no)
    WHERE request_no IS NOT NULL;

CREATE INDEX idx_override_request_no ON manual_override(request_no);
