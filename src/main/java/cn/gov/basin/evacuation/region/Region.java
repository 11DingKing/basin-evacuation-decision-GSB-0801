package cn.gov.basin.evacuation.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "regions")
public class Region {

    @Id
    @Column(length = 12)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 12)
    private String parentCode;

    @Column(nullable = false)
    private Short level;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Region() {
    }

    public Region(String code, String name, String parentCode, Short level, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.parentCode = parentCode;
        this.level = level;
        this.createdAt = createdAt;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getParentCode() { return parentCode; }
    public Short getLevel() { return level; }
    public Instant getCreatedAt() { return createdAt; }
}
