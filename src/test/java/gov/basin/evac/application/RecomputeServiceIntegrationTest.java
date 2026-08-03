package gov.basin.evac.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
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
import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.model.AdvisorySource;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.model.HazardPointStatus;
import gov.basin.evac.domain.model.OutboxStatus;
import gov.basin.evac.domain.model.RoadStatus;
import gov.basin.evac.domain.model.UpstreamSource;
import gov.basin.evac.domain.model.UpstreamStatus;
import gov.basin.evac.domain.repository.AdvisoryRepository;
import gov.basin.evac.domain.repository.NotificationOutboxRepository;
import gov.basin.evac.support.ControllableNotificationSender;
import gov.basin.evac.support.MutableClock;
import gov.basin.evac.support.PostgresTestContainer;
import gov.basin.evac.support.TestBeansConfig;

/**
 * End-to-end integration tests against a real PostgreSQL (Testcontainers). Exercises the exact
 * adversarial scenarios in the brief: clock frozen before / at / after override expiry, concurrent
 * recompute of one snapshot, two upstream timeouts + one stale version, notification send failure
 * with outbox replay, and verification that no half-written state is ever left behind.
 */
@SpringBootTest
@Import({PostgresTestContainer.class, TestBeansConfig.class})
class RecomputeServiceIntegrationTest {

    private static final String SEED_SNAPSHOT = "sc-510182-20260729T0300";

    @Autowired
    private RecomputeService recomputeService;
    @Autowired
    private OverrideService overrideService;
    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    private OutboxDispatcher outboxDispatcher;
    @Autowired
    private AdvisoryRepository advisoryRepository;
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
        clock.setInstant(TestBeansConfig.ANCHOR);
        sender.failNext(false);
        sender.delivered().clear();
        // Each test starts from the pristine Flyway seed. TRUNCATE does not fire the row-level
        // immutability triggers (which guard UPDATE/DELETE), so it is the correct way to reset
        // the shared container between tests; we then re-seed the initial snapshot.
        jdbc.execute("TRUNCATE notification_outbox, advisories, manual_overrides, "
                + "snapshot_upstream_health, risk_snapshots RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO risk_snapshots (snapshot_id, region_code, evidence_version, observed_at, "
                + "rainfall_3h_mm, river_level_m, hazard_point_status, primary_road_status, "
                + "secondary_road_status, vulnerable_population_persons) VALUES "
                + "(?, '510182', 1, TIMESTAMPTZ '2026-07-29 03:00:00+00', 118.00, 6.12, "
                + "'WARNING', 'CLOSED', 'UNKNOWN', 286)", SEED_SNAPSHOT);
        for (String[] feed : new String[][]{
                {"RAINFALL", "rain-v1"}, {"RIVER_LEVEL", "river-v1"},
                {"HAZARD", "haz-v1"}, {"ROAD", "road-v1"}}) {
            jdbc.update("INSERT INTO snapshot_upstream_health (snapshot_id, source, status, "
                    + "reported_version, observed_at) VALUES (?, ?, 'OK', ?, "
                    + "TIMESTAMPTZ '2026-07-29 03:00:00+00')", SEED_SNAPSHOT, feed[0], feed[1]);
        }
        jdbc.queryForObject("SELECT setval('evidence_version_seq', 1, true)", Long.class);
    }

    @Test
    void seedSnapshotComputesMoveNowCitingEvidenceVersionOne() {
        Advisory advisory = recomputeService.recompute(SEED_SNAPSHOT);
        assertThat(advisory.getDecisionLevel()).isEqualTo(DecisionLevel.MOVE_NOW);
        assertThat(advisory.getSource()).isEqualTo(AdvisorySource.COMPUTED);
        assertThat(advisory.getEvidenceVersion()).isEqualTo(1L);
        assertThat(advisory.getAffectedPersons()).isEqualTo(286);
    }

    @Test
    void overrideActiveBeforeExactlyAtAndAfterExpiry() {
        Instant from = TestBeansConfig.ANCHOR;
        // Keep the window inside the evidence-freshness horizon so the reverted result is the
        // genuine computed MOVE_NOW rather than an INSUFFICIENT_DATA from stale evidence.
        Instant expiry = from.plus(Duration.ofMinutes(10));

        // Create override while clock is before expiry.
        overrideService.createOverride(SEED_SNAPSHOT, DecisionLevel.WATCH,
                "值班员张三", "上游数据存疑，暂降级观察", from, expiry);

        // (a) Frozen before expiry -> override in force.
        clock.setInstant(expiry.minusSeconds(1));
        Advisory before = recomputeService.recompute(SEED_SNAPSHOT);
        assertThat(before.getSource()).isEqualTo(AdvisorySource.OVERRIDE);
        assertThat(before.getDecisionLevel()).isEqualTo(DecisionLevel.WATCH);

        // (b) Frozen exactly at expiry -> window is half-open, override already inactive.
        clock.setInstant(expiry);
        Advisory atExpiry = recomputeService.recompute(SEED_SNAPSHOT);
        assertThat(atExpiry.getSource()).isEqualTo(AdvisorySource.COMPUTED);
        assertThat(atExpiry.getDecisionLevel()).isEqualTo(DecisionLevel.MOVE_NOW);

        // (c) Frozen one instant after expiry -> still the computed result (auto-reverted).
        clock.setInstant(expiry.plusNanos(1_000_000));
        Advisory after = recomputeService.recompute(SEED_SNAPSHOT);
        assertThat(after.getSource()).isEqualTo(AdvisorySource.COMPUTED);

        // History (override + computed advisories) is fully preserved, nothing deleted.
        List<Advisory> history = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(SEED_SNAPSHOT);
        assertThat(history).extracting(Advisory::getSource)
                .contains(AdvisorySource.OVERRIDE, AdvisorySource.COMPUTED);
        assertThat(advisoryRepository.findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(SEED_SNAPSHOT))
                .get().extracting(Advisory::getSource).isEqualTo(AdvisorySource.COMPUTED);
    }

    @Test
    void concurrentRecomputeOfSameSnapshotYieldsExactlyOneCurrentAdvisory() throws Exception {
        recomputeService.recompute(SEED_SNAPSHOT); // establish baseline

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Advisory>> tasks = java.util.Collections.nCopies(threads,
                    () -> recomputeService.recompute(SEED_SNAPSHOT));
            List<Future<Advisory>> futures = pool.invokeAll(tasks);
            for (Future<Advisory> f : futures) {
                f.get(); // propagate any failure
            }
        } finally {
            pool.shutdownNow();
        }

        // Exactly one non-superseded advisory survives despite concurrent recomputes.
        List<Advisory> all = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(SEED_SNAPSHOT);
        long current = all.stream().filter(a -> !a.isSuperseded()).count();
        assertThat(current).isEqualTo(1L);
    }

    @Test
    void twoUpstreamTimeoutsAndOneStaleProduceInsufficientDataNotLow() {
        // Ingest a snapshot with benign readings but degraded upstream health.
        String id = "sc-510182-degraded";
        Instant observed = clock.instant();
        snapshotService.ingest(id, "510182", observed,
                BigDecimal.valueOf(5), BigDecimal.valueOf(1.0),
                HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN, 286,
                List.of(
                        new SnapshotService.UpstreamHealthInput(UpstreamSource.RAINFALL, UpstreamStatus.TIMEOUT, null, observed),
                        new SnapshotService.UpstreamHealthInput(UpstreamSource.RIVER_LEVEL, UpstreamStatus.TIMEOUT, null, observed),
                        new SnapshotService.UpstreamHealthInput(UpstreamSource.HAZARD, UpstreamStatus.STALE, "old-haz", observed),
                        new SnapshotService.UpstreamHealthInput(UpstreamSource.ROAD, UpstreamStatus.OK, "road-v1", observed)));

        Advisory advisory = advisoryRepository
                .findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(id).orElseThrow();
        assertThat(advisory.getDecisionLevel()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
        assertThat(advisory.getDecisionLevel()).isNotEqualTo(DecisionLevel.LOW);
    }

    @Test
    void notificationSendFailureLeavesReplayableOutboxThenSucceedsOnRetry() {
        // Recompute writes advisory + outbox atomically.
        recomputeService.recompute(SEED_SNAPSHOT);
        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1L);

        // First delivery attempt fails; advisory persists, outbox flips to FAILED (replayable).
        sender.failNext(true);
        int delivered = outboxDispatcher.dispatchPending();
        assertThat(delivered).isZero();
        assertThat(outboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1L);
        assertThat(outboxRepository.countByStatus(OutboxStatus.SENT)).isZero();
        // The advisory itself was NOT rolled back by the failed notification.
        assertThat(advisoryRepository.findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(SEED_SNAPSHOT))
                .isPresent();

        // Recover the channel and replay: the same record now delivers, no duplicates.
        sender.failNext(false);
        int redelivered = outboxDispatcher.dispatchPending();
        assertThat(redelivered).isEqualTo(1);
        assertThat(outboxRepository.countByStatus(OutboxStatus.SENT)).isEqualTo(1L);
        assertThat(sender.delivered()).hasSize(1);
        assertThat(sender.delivered().get(0)).contains("证据版本v1");
    }

    @Test
    void everyAdvisoryHasMatchingOutboxRecord_noHalfWrittenState() {
        recomputeService.recompute(SEED_SNAPSHOT);
        // Force a change so a second advisory is written.
        overrideService.createOverride(SEED_SNAPSHOT, DecisionLevel.MOVE_PREPARE,
                "值班员李四", "结合现场研判下调", clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        List<Advisory> advisories = advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(SEED_SNAPSHOT);
        // Each advisory must have a corresponding outbox row citing the same evidence version.
        for (Advisory a : advisories) {
            boolean hasOutbox = outboxRepository.findAll().stream()
                    .anyMatch(o -> o.getAdvisoryId().equals(a.getId())
                            && o.getEvidenceVersion() == a.getEvidenceVersion());
            assertThat(hasOutbox)
                    .as("advisory %d must have a matching outbox record", a.getId())
                    .isTrue();
        }
    }

    @Test
    void immutableSnapshotAlsoRecordsFourUpstreamHealthEntries() {
        RiskSnapshot snapshot = snapshotService.get(SEED_SNAPSHOT);
        assertThat(snapshot.getUpstreamHealth()).hasSize(4);
        assertThat(snapshot.getVulnerablePopulationPersons()).isEqualTo(286);
    }
}
