package gov.basin.evac.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import gov.basin.evac.domain.entity.Advisory;
import gov.basin.evac.domain.entity.ManualOverride;
import gov.basin.evac.domain.model.AdvisorySource;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.repository.AdvisoryRepository;
import gov.basin.evac.domain.repository.ManualOverrideRepository;
import gov.basin.evac.domain.repository.NotificationOutboxRepository;
import gov.basin.evac.support.ControllableNotificationSender;
import gov.basin.evac.support.MutableClock;
import gov.basin.evac.support.PostgresTestContainer;
import gov.basin.evac.support.TestBeansConfig;

/**
 * Integration tests for the second snapshot (彭州市 06:00) and idempotent, request-id-keyed
 * manual overrides. Verifies against real PostgreSQL that:
 * <ul>
 *   <li>the two snapshots keep independent evidence and decision histories;</li>
 *   <li>replaying the same business request number — even concurrently — produces exactly one
 *       override, one override-sourced advisory, and one outbox record;</li>
 *   <li>notifications carry snapshot id, evidence version, upstream versions and request id.</li>
 * </ul>
 */
@SpringBootTest
@Import({PostgresTestContainer.class, TestBeansConfig.class})
class SecondSnapshotOverrideIntegrationTest {

    private static final String OLD_SNAPSHOT = "sc-510182-20260729T0300";
    private static final String NEW_SNAPSHOT = "sc-510182-20260729T0600";

    @Autowired
    private RecomputeService recomputeService;
    @Autowired
    private OverrideService overrideService;
    @Autowired
    private AdvisoryRepository advisoryRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private NotificationOutboxRepository outboxRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ControllableNotificationSender sender;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        // Anchor the clock at 06:05, right after the 06:00 snapshot, so both snapshots' evidence
        // is fresh (within the 30-minute horizon) and computes a genuine risk level.
        clock.setInstant(Instant.parse("2026-07-29T06:05:00Z"));
        sender.failNext(false);
        sender.delivered().clear();

        jdbc.execute("TRUNCATE notification_outbox, advisories, manual_overrides, "
                + "snapshot_upstream_health, risk_snapshots RESTART IDENTITY CASCADE");

        seedSnapshot(OLD_SNAPSHOT, 1, "2026-07-29 03:00:00+00", "118.00", "6.12",
                "WARNING", "CLOSED", "UNKNOWN", "20260729T0300");
        seedSnapshot(NEW_SNAPSHOT, 2, "2026-07-29 06:00:00+00", "164.00", "6.18",
                "WARNING", "OPEN", "UNKNOWN", "20260729T0600");
        jdbc.queryForObject("SELECT setval('evidence_version_seq', 2, true)", Long.class);
    }

    private void seedSnapshot(String id, long version, String observedAt, String rain, String river,
                              String hazard, String primary, String secondary, String upstreamVersion) {
        jdbc.update("INSERT INTO risk_snapshots (snapshot_id, region_code, evidence_version, observed_at, "
                + "rainfall_3h_mm, river_level_m, hazard_point_status, primary_road_status, "
                + "secondary_road_status, vulnerable_population_persons) VALUES "
                + "(?, '510182', ?, TIMESTAMPTZ '" + observedAt + "', ?, ?, ?, ?, ?, 286)",
                id, version, new java.math.BigDecimal(rain), new java.math.BigDecimal(river),
                hazard, primary, secondary);
        for (String source : new String[]{"RAINFALL", "RIVER_LEVEL", "HAZARD", "ROAD"}) {
            jdbc.update("INSERT INTO snapshot_upstream_health (snapshot_id, source, status, "
                    + "reported_version, observed_at) VALUES (?, ?, 'OK', ?, "
                    + "TIMESTAMPTZ '" + observedAt + "')", id, source, upstreamVersion);
        }
    }

    @Test
    void newSnapshotComputesMoveNowFromRainfallAndRiver() {
        Advisory advisory = recomputeService.recompute(NEW_SNAPSHOT);
        // Rainfall 164 (>=100) and river 6.18 (>=6.0) both hit MOVE_NOW even though primary road is OPEN.
        assertThat(advisory.getDecisionLevel()).isEqualTo(DecisionLevel.MOVE_NOW);
        assertThat(advisory.getEvidenceVersion()).isEqualTo(2L);
        assertThat(advisory.getSource()).isEqualTo(AdvisorySource.COMPUTED);
    }

    @Test
    void twoSnapshotsKeepIndependentEvidenceAndDecisionHistories() {
        recomputeService.recompute(OLD_SNAPSHOT);
        recomputeService.recompute(NEW_SNAPSHOT);
        // Override only the OLD snapshot.
        overrideService.createOverride(OLD_SNAPSHOT, DecisionLevel.WATCH, "值班员赵六",
                "老快照人工降级", clock.instant(), clock.instant().plus(Duration.ofMinutes(30)),
                "override-17");

        // OLD snapshot now reflects the override; NEW snapshot is untouched.
        Advisory oldCurrent = advisoryRepository
                .findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(OLD_SNAPSHOT).orElseThrow();
        Advisory newCurrent = advisoryRepository
                .findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(NEW_SNAPSHOT).orElseThrow();
        assertThat(oldCurrent.getSource()).isEqualTo(AdvisorySource.OVERRIDE);
        assertThat(oldCurrent.getEvidenceVersion()).isEqualTo(1L);
        assertThat(newCurrent.getSource()).isEqualTo(AdvisorySource.COMPUTED);
        assertThat(newCurrent.getEvidenceVersion()).isEqualTo(2L);

        // Histories do not bleed across snapshots.
        List<Advisory> oldHistory = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(OLD_SNAPSHOT);
        List<Advisory> newHistory = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(NEW_SNAPSHOT);
        assertThat(oldHistory).allMatch(a -> a.getSnapshotId().equals(OLD_SNAPSHOT));
        assertThat(newHistory).allMatch(a -> a.getSnapshotId().equals(NEW_SNAPSHOT));
        assertThat(overrideRepository.findBySnapshotIdOrderByEffectiveFromDesc(NEW_SNAPSHOT)).isEmpty();
    }

    @Test
    void requestIdOverrideIsIdempotentOnSequentialReplay() {
        Instant from = clock.instant();
        Instant expiry = from.plus(Duration.ofMinutes(30));

        ManualOverride first = overrideService.createOverride(OLD_SNAPSHOT, DecisionLevel.MOVE_NOW,
                "值班员钱七", "紧急提级", from, expiry, "override-17");
        ManualOverride replay = overrideService.createOverride(OLD_SNAPSHOT, DecisionLevel.MOVE_NOW,
                "值班员钱七", "紧急提级", from, expiry, "override-17");

        // Same override row, no duplicates anywhere.
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(overrideRepository.findAll()).hasSize(1);
        long overrideAdvisories = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(OLD_SNAPSHOT)
                .stream().filter(a -> a.getSource() == AdvisorySource.OVERRIDE).count();
        assertThat(overrideAdvisories).isEqualTo(1L);
        long currentCount = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(OLD_SNAPSHOT)
                .stream().filter(a -> !a.isSuperseded()).count();
        assertThat(currentCount).isEqualTo(1L);
    }

    @Test
    void requestIdOverrideIsIdempotentUnderConcurrentReplay() throws Exception {
        Instant from = clock.instant();
        Instant expiry = from.plus(Duration.ofMinutes(30));

        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<ManualOverride>> tasks = java.util.Collections.nCopies(threads, () -> {
                barrier.await(); // maximise contention: fire all at once
                return overrideService.createOverride(OLD_SNAPSHOT, DecisionLevel.MOVE_NOW,
                        "值班员孙八", "并发重放", from, expiry, "override-17");
            });
            List<Future<ManualOverride>> futures = pool.invokeAll(tasks);
            Long id = null;
            for (Future<ManualOverride> f : futures) {
                ManualOverride ov = f.get();
                if (id == null) {
                    id = ov.getId();
                }
                assertThat(ov.getId()).isEqualTo(id); // every caller sees the same override
            }
        } finally {
            pool.shutdownNow();
        }

        // Exactly one override, one override-advisory, one current advisory, one outbox for it.
        assertThat(overrideRepository.findByRequestId("override-17")).isPresent();
        assertThat(overrideRepository.findAll()).hasSize(1);
        List<Advisory> history = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(OLD_SNAPSHOT);
        assertThat(history.stream().filter(a -> a.getSource() == AdvisorySource.OVERRIDE).count()).isEqualTo(1L);
        assertThat(history.stream().filter(a -> !a.isSuperseded()).count()).isEqualTo(1L);
        long overrideOutbox = outboxRepository.findAll().stream()
                .filter(o -> history.stream().anyMatch(a -> a.getId().equals(o.getAdvisoryId())
                        && a.getSource() == AdvisorySource.OVERRIDE))
                .count();
        assertThat(overrideOutbox).isEqualTo(1L);
    }

    @Test
    void notificationCarriesSnapshotVersionUpstreamVersionsAndRequestId() {
        overrideService.createOverride(OLD_SNAPSHOT, DecisionLevel.MOVE_NOW, "值班员周九",
                "带请求号覆写", clock.instant(), clock.instant().plus(Duration.ofMinutes(30)), "override-17");

        String payload = outboxRepository.findAll().stream()
                .max((a, b) -> a.getId().compareTo(b.getId()))
                .orElseThrow().getPayload();

        assertThat(payload).contains(OLD_SNAPSHOT);          // snapshot id
        assertThat(payload).contains("证据版本v1");           // evidence version
        assertThat(payload).contains("RAINFALL=20260729T0300"); // upstream versions
        assertThat(payload).contains("请求号override-17");    // request id
    }
}
