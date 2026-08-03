package com.example.basin.evacuation.domain.district;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "district")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AdministrativeDistrict {

    @Id
    @Column(length = 12)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_code", length = 12)
    private String parentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DistrictLevel level;
}
