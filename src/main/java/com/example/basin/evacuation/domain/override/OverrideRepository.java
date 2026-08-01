package com.example.basin.evacuation.domain.override;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OverrideRepository extends JpaRepository<ManualOverride, Long> {

    @Query("""
            select o from ManualOverride o
            where o.snapshotId = :snapshotId
              and o.expiresAt > :now
            order by o.id desc
            limit 1
            """)
    Optional<ManualOverride> findActiveOverride(@Param("snapshotId") String snapshotId,
                                                @Param("now") Instant now);

    List<ManualOverride> findBySnapshotIdOrderByIdDesc(String snapshotId);
}
