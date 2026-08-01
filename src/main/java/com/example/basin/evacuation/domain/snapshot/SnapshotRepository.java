package com.example.basin.evacuation.domain.snapshot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<RiskSnapshot, String> {

    List<RiskSnapshot> findByDistrictCodeOrderByObservedAtDesc(String districtCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RiskSnapshot s where s.snapshotId = :id")
    Optional<RiskSnapshot> findForUpdate(@Param("id") String snapshotId);
}
