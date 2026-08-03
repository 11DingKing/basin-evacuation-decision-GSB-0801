package com.basin.evacuation.region;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, String> {

    Page<Region> findByNameContainingOrCodeContaining(String name, String code, Pageable pageable);

    /** 悲观写锁：并发创建同一行政区的快照时串行化，保证快照主键检查-插入无竞态 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Region r where r.code = :code")
    Optional<Region> lockByCode(@Param("code") String code);
}
