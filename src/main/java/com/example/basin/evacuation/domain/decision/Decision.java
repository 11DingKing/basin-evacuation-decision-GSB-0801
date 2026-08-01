package com.example.basin.evacuation.domain.decision;

import com.example.basin.evacuation.domain.shared.DecisionLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "decision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, length = 64)
    private String snapshotId;

    @Column(name = "district_code", nullable = false, length = 12)
    private String districtCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DecisionLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "computed_level", nullable = false, length = 30)
    private DecisionLevel computedLevel;

    @Column(name = "active_override_id")
    private Long activeOverrideId;

    @Column(name = "evidence_version", nullable = false, length = 100)
    private String evidenceVersion;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimension_breakdown", nullable = false, columnDefinition = "jsonb")
    private DimensionBreakdown dimensionBreakdown;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
