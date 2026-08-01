package com.example.basin.evacuation.domain.decision;

import com.example.basin.evacuation.domain.shared.DecisionLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    Optional<Decision> findFirstBySnapshotIdOrderBySequenceNoDesc(String snapshotId);

    List<Decision> findBySnapshotIdOrderBySequenceNoDesc(String snapshotId);

    Page<Decision> findByDistrictCodeOrderByCreatedAtDesc(String districtCode, Pageable pageable);

    Page<Decision> findByLevelOrderByCreatedAtDesc(DecisionLevel level, Pageable pageable);

    Page<Decision> findByDistrictCodeAndLevelOrderByCreatedAtDesc(String districtCode, DecisionLevel level, Pageable pageable);

    @Query("select coalesce(max(d.sequenceNo), 0) from Decision d where d.snapshotId = :snapshotId")
    int maxSequenceNoForSnapshot(@Param("snapshotId") String snapshotId);
}
