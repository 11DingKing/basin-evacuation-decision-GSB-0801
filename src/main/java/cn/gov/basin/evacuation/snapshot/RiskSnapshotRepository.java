package cn.gov.basin.evacuation.snapshot;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskSnapshotRepository extends JpaRepository<RiskSnapshot, String> {

    Page<RiskSnapshot> findByRegionCodeOrderByObservedAtDesc(String regionCode, Pageable pageable);

    Optional<RiskSnapshot> findFirstByRegionCodeOrderByObservedAtDesc(String regionCode);
}
