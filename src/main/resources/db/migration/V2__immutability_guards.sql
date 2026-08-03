-- V2: Enforce immutability and no-delete guarantees at the database level.
-- Risk snapshots and manual overrides are immutable; advisories, overrides and snapshots
-- can never be deleted (only superseded logically). This protects the audit history even
-- against direct SQL access.

CREATE OR REPLACE FUNCTION forbid_operation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Operation % on % is forbidden: rows are immutable/non-deletable',
        TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- Immutable risk snapshots: no UPDATE, no DELETE.
CREATE TRIGGER trg_snapshots_no_update
    BEFORE UPDATE ON risk_snapshots
    FOR EACH ROW EXECUTE FUNCTION forbid_operation();

CREATE TRIGGER trg_snapshots_no_delete
    BEFORE DELETE ON risk_snapshots
    FOR EACH ROW EXECUTE FUNCTION forbid_operation();

-- Manual overrides: created once, never edited, never deleted (expiry handles retirement).
CREATE TRIGGER trg_overrides_no_update
    BEFORE UPDATE ON manual_overrides
    FOR EACH ROW EXECUTE FUNCTION forbid_operation();

CREATE TRIGGER trg_overrides_no_delete
    BEFORE DELETE ON manual_overrides
    FOR EACH ROW EXECUTE FUNCTION forbid_operation();

-- Advisories: append-only history. The only mutation allowed is flipping superseded FALSE->TRUE.
CREATE OR REPLACE FUNCTION advisory_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Advisories cannot be deleted';
    END IF;
    -- Allow only the supersede flag to change; everything else is frozen.
    IF NEW.snapshot_id      IS DISTINCT FROM OLD.snapshot_id
       OR NEW.region_code   IS DISTINCT FROM OLD.region_code
       OR NEW.evidence_version IS DISTINCT FROM OLD.evidence_version
       OR NEW.decision_level IS DISTINCT FROM OLD.decision_level
       OR NEW.priority       IS DISTINCT FROM OLD.priority
       OR NEW.source         IS DISTINCT FROM OLD.source
       OR NEW.rationale      IS DISTINCT FROM OLD.rationale
       OR NEW.computed_at    IS DISTINCT FROM OLD.computed_at THEN
        RAISE EXCEPTION 'Advisory content is immutable; only supersede flag may change';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_advisories_guard_update
    BEFORE UPDATE ON advisories
    FOR EACH ROW EXECUTE FUNCTION advisory_guard();

CREATE TRIGGER trg_advisories_guard_delete
    BEFORE DELETE ON advisories
    FOR EACH ROW EXECUTE FUNCTION advisory_guard();

-- Upstream health rows belong to an immutable snapshot; freeze them too.
CREATE TRIGGER trg_upstream_no_update
    BEFORE UPDATE ON snapshot_upstream_health
    FOR EACH ROW EXECUTE FUNCTION forbid_operation();

CREATE TRIGGER trg_upstream_no_delete
    BEFORE DELETE ON snapshot_upstream_health
    FOR EACH ROW EXECUTE FUNCTION forbid_operation();
