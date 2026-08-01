package com.basin.evacuation.decision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRepository extends JpaRepository<Decision, UUID>, JpaSpecificationExecutor<Decision> {

    @Query("select max(d.seq) from Decision d where d.snapshotId = :snapshotId")
    Optional<Integer> findMaxSeq(@Param("snapshotId") String snapshotId);

    Optional<Decision> findFirstBySnapshotIdAndSourceOrderBySeqDesc(String snapshotId, DecisionSource source);

    Optional<Decision> findByOverrideId(UUID overrideId);

    List<Decision> findBySnapshotIdOrderBySeqDesc(String snapshotId);
}
