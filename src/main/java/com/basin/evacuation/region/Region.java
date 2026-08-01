package com.basin.evacuation.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "region")
public class Region {

    @Id
    @Column(name = "code", length = 12)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "parent_code")
    private String parentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 16)
    private RegionLevel level;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Region() {}

    public Region(String code, String name, String parentCode, RegionLevel level, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.parentCode = parentCode;
        this.level = level;
        this.createdAt = createdAt;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getParentCode() {
        return parentCode;
    }

    public RegionLevel getLevel() {
        return level;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
