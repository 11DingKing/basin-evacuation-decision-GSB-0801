package com.example.basin.evacuation.domain.district;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistrictRepository extends JpaRepository<AdministrativeDistrict, String> {

    List<AdministrativeDistrict> findByParentCode(String parentCode);

    List<AdministrativeDistrict> findByLevel(DistrictLevel level);
}
