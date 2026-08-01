package com.example.basin.evacuation.domain;

import com.example.basin.evacuation.application.DecisionService;
import com.example.basin.evacuation.support.AbstractIntegrationTest;
import com.example.basin.evacuation.support.SnapshotTestFactory;
import com.example.basin.evacuation.application.SnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ImmutabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DecisionService decisionService;
    @Autowired SnapshotService snapshotService;

    @Test
    void snapshotDecisionAndOverrideCannotBeUpdatedOrDeleted() {
        String snapshotId = "snap-immutable-" + System.nanoTime();
        snapshotService.ingest(SnapshotTestFactory.healthy(snapshotId).build());
        decisionService.recompute(snapshotId);

        assertThatThrownBy(() -> jdbc.update(
                "update risk_snapshot set evidence_version = ? where snapshot_id = ?",
                "tampered", snapshotId))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update(
                "delete from risk_snapshot where snapshot_id = ?", snapshotId))
                .hasMessageContaining("append-only");

        Long decisionId = jdbc.queryForObject(
                "select id from decision where snapshot_id = ? order by sequence_no desc limit 1",
                Long.class, snapshotId);

        assertThatThrownBy(() -> jdbc.update(
                "update decision set level = 'RED' where id = ?", decisionId))
                .hasMessageContaining("append-only");

        jdbc.update("""
                insert into manual_override (snapshot_id, operator, reason, target_level, expires_at, created_at)
                values (?, 'op', 'r', 'RED', now() + interval '1 hour', now())
                """, snapshotId);

        Long overrideId = jdbc.queryForObject(
                "select id from manual_override where snapshot_id = ? order by id desc limit 1",
                Long.class, snapshotId);

        assertThatThrownBy(() -> jdbc.update(
                "delete from manual_override where id = ?", overrideId))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update(
                "delete from notification_outbox where decision_id = ?", decisionId))
                .hasMessageContaining("append-only");
    }
}
