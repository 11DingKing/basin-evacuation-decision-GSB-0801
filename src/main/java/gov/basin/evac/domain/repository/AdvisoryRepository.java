package gov.basin.evac.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gov.basin.evac.domain.entity.Advisory;
import gov.basin.evac.domain.model.DecisionLevel;

public interface AdvisoryRepository extends JpaRepository<Advisory, Long> {

    Optional<Advisory> findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(String snapshotId);

    List<Advisory> findBySnapshotIdOrderByComputedAtDesc(String snapshotId);

    /** Search/detail support: filter current advisories by region and/or level. */
    @Query("""
            select a from Advisory a
            where (:regionCode is null or a.regionCode = :regionCode)
              and (:level is null or a.decisionLevel = :level)
              and (:currentOnly = false or a.superseded = false)
            order by a.computedAt desc
            """)
    Page<Advisory> search(@Param("regionCode") String regionCode,
                          @Param("level") DecisionLevel level,
                          @Param("currentOnly") boolean currentOnly,
                          Pageable pageable);

    /** Atomically supersede every currently-active advisory for a snapshot. */
    @Modifying
    @Query("update Advisory a set a.superseded = true where a.snapshotId = :snapshotId and a.superseded = false")
    int supersedeCurrent(@Param("snapshotId") String snapshotId);
}
