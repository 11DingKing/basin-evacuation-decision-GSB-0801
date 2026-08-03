package com.basin.evacuation.snapshot;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskSnapshotRepository extends JpaRepository<RiskSnapshot, String> {

    /**
     * 悲观写锁：并发重算同一快照时按行锁串行化，
     * 配合 decision(snapshot_id, seq) 唯一约束保证序号不重、状态不写半。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RiskSnapshot s where s.snapshotId = :snapshotId")
    Optional<RiskSnapshot> lockBySnapshotId(@Param("snapshotId") String snapshotId);

    Page<RiskSnapshot> findByRegionCode(String regionCode, Pageable pageable);
}
