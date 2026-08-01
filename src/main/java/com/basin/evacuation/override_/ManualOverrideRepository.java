package com.basin.evacuation.override_;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualOverrideRepository extends JpaRepository<ManualOverride, UUID> {

    /** 取当前生效的覆写（expiresAt > now，最新创建者优先） */
    Optional<ManualOverride> findFirstBySnapshotIdAndExpiresAtGreaterThanOrderByCreatedAtDesc(
            String snapshotId, Instant now);

    List<ManualOverride> findBySnapshotIdOrderByCreatedAtDesc(String snapshotId);
}
