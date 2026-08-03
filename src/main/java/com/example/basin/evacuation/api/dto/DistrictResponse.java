package com.example.basin.evacuation.api.dto;

import com.example.basin.evacuation.domain.district.AdministrativeDistrict;
import com.example.basin.evacuation.domain.district.DistrictLevel;

public record DistrictResponse(
        String code,
        String name,
        String parentCode,
        DistrictLevel level
) {
    public static DistrictResponse from(AdministrativeDistrict d) {
        return new DistrictResponse(d.getCode(), d.getName(), d.getParentCode(), d.getLevel());
    }
}
