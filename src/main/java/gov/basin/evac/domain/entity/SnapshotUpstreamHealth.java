package gov.basin.evac.domain.entity;

import java.time.Instant;

import gov.basin.evac.domain.model.UpstreamSource;
import gov.basin.evac.domain.model.UpstreamStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** The recorded health of a single upstream feed for a snapshot. Immutable once written. */
@Entity
@Table(name = "snapshot_upstream_health")
public class SnapshotUpstreamHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false, updatable = false)
    private RiskSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private UpstreamSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private UpstreamStatus status;

    @Column(name = "reported_version", updatable = false)
    private String reportedVersion;

    @Column(name = "observed_at", updatable = false)
    private Instant observedAt;

    protected SnapshotUpstreamHealth() {
    }

    public SnapshotUpstreamHealth(UpstreamSource source, UpstreamStatus status,
                                  String reportedVersion, Instant observedAt) {
        this.source = source;
        this.status = status;
        this.reportedVersion = reportedVersion;
        this.observedAt = observedAt;
    }

    void attachTo(RiskSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Long getId() {
        return id;
    }

    public UpstreamSource getSource() {
        return source;
    }

    public UpstreamStatus getStatus() {
        return status;
    }

    public String getReportedVersion() {
        return reportedVersion;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
