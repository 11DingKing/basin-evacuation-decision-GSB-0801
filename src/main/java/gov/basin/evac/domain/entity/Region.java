package gov.basin.evac.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An administrative region (行政区). */
@Entity
@Table(name = "regions")
public class Region {

    @Id
    @Column(name = "region_code", length = 12, updatable = false)
    private String regionCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "parent_code")
    private String parentCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Region() {
    }

    public Region(String regionCode, String name, String parentCode) {
        this.regionCode = regionCode;
        this.name = name;
        this.parentCode = parentCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getName() {
        return name;
    }

    public String getParentCode() {
        return parentCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
