package gov.basin.evac.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gov.basin.evac.domain.entity.Region;

public interface RegionRepository extends JpaRepository<Region, String> {
}
