-- =====================================================================
-- V5__decision_request_no.sql : idempotent recompute.
-- A recompute can be driven by an external business request number
-- (e.g. "recompute-expiry-17"). The unique partial index guarantees
-- that concurrent retries of the same request produce at most one new
-- decision row. NULL request_no is allowed for ordinary recomputes
-- (multiple NULLs never conflict).
-- =====================================================================

ALTER TABLE decision
    ADD COLUMN request_no VARCHAR(64);

CREATE UNIQUE INDEX uq_decision_request_no
    ON decision(request_no)
    WHERE request_no IS NOT NULL;

CREATE INDEX idx_decision_request_no ON decision(request_no);
