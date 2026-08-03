package gov.basin.evac.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gov.basin.evac.domain.entity.RiskSnapshot;
import jakarta.persistence.LockModeType;

public interface RiskSnapshotRepository extends JpaRepository<RiskSnapshot, String> {

    /**
     * Acquires a row-level lock on the snapshot so that concurrent recomputes of the SAME
     * snapshot are serialized: only one transaction may supersede/insert advisories at a time,
     * preventing duplicate "current" advisories.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RiskSnapshot s where s.snapshotId = :id")
    Optional<RiskSnapshot> findByIdForUpdate(@Param("id") String id);

    /** Read path: eagerly fetch upstream health so callers can read it after the tx closes. */
    @Query("select s from RiskSnapshot s left join fetch s.upstreamHealth where s.snapshotId = :id")
    Optional<RiskSnapshot> findByIdWithHealth(@Param("id") String id);
}
