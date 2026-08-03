package cn.gov.basin.evacuation.region;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findByParentCode(String parentCode);
}
