package gov.basin.evac.api.dto;

import gov.basin.evac.domain.entity.Region;
import jakarta.validation.constraints.NotBlank;

/** API view / create-request for an administrative region. */
public record RegionDto(
        @NotBlank String regionCode,
        @NotBlank String name,
        String parentCode) {

    public static RegionDto from(Region r) {
        return new RegionDto(r.getRegionCode(), r.getName(), r.getParentCode());
    }
}
