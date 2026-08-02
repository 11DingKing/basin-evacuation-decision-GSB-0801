package cn.gov.basin.evacuation.override;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OverrideRepository extends JpaRepository<ManualOverride, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ManualOverride o where o.regionCode = :region and o.status = 'ACTIVE' "
            + "and o.effectiveFrom <= :now and o.expiresAt > :now "
            + "order by o.createdAt desc, o.id desc")
    List<ManualOverride> findActiveForRegionAt(@Param("region") String region,
                                               @Param("now") Instant now);

    @Query("select o from ManualOverride o where o.regionCode = :region "
            + "order by o.createdAt desc, o.id desc")
    List<ManualOverride> findHistoryForRegion(@Param("region") String region);

    @Modifying
    @Query("update ManualOverride o set o.status = 'EXPIRED' "
            + "where o.status = 'ACTIVE' and o.expiresAt <= :now")
    int expireDueOverrides(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ManualOverride o where o.id = :id")
    Optional<ManualOverride> lockById(@Param("id") Long id);

    Optional<ManualOverride> findByRequestId(String requestId);
}
