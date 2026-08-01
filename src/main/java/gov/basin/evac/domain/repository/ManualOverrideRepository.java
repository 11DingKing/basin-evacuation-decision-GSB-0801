package gov.basin.evac.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import gov.basin.evac.domain.entity.ManualOverride;

public interface ManualOverrideRepository extends JpaRepository<ManualOverride, Long> {

    /** All overrides for a snapshot, most recent first (history is never deleted). */
    List<ManualOverride> findBySnapshotIdOrderByEffectiveFromDesc(String snapshotId);

    Optional<ManualOverride> findFirstBySnapshotIdOrderByEffectiveFromDesc(String snapshotId);

    /** Lookup by the business request number; underpins idempotent override creation. */
    Optional<ManualOverride> findByRequestId(String requestId);
}
