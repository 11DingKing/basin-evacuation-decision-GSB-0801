package com.basin.evacuation.region;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, String> {

    Page<Region> findByNameContainingOrCodeContaining(String name, String code, Pageable pageable);
}
