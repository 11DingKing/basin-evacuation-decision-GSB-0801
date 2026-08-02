package cn.gov.basin.evacuation.decision;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DecisionAdviceRepository extends JpaRepository<DecisionAdvice, Long> {

    Optional<DecisionAdvice> findFirstBySnapshotIdOrderByComputedAtDesc(String snapshotId);

    Optional<DecisionAdvice> findFirstBySnapshotIdAndRequestIdOrderByComputedAtDesc(
            String snapshotId, String requestId);

    Page<DecisionAdvice> findByRegionCodeOrderByComputedAtDesc(String regionCode, Pageable pageable);

    @Query("select a from DecisionAdvice a where a.regionCode = :region "
            + "and a.decisionLevel = :level order by a.computedAt desc")
    Page<DecisionAdvice> findByRegionAndLevel(@Param("region") String region,
                                              @Param("level") DecisionLevel level,
                                              Pageable pageable);

    @Query("select a from DecisionAdvice a order by a.computedAt desc")
    Page<DecisionAdvice> findLatest(Pageable pageable);
}
