package com.example.basin.evacuation.domain.override;

import com.example.basin.evacuation.application.DecisionService;
import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.decision.DecisionRepository;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.support.AbstractIntegrationTest;
import com.example.basin.evacuation.support.TestClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OverrideExpiryIntegrationTest extends AbstractIntegrationTest {

    private static final String SNAPSHOT = "sc-510182-20260729T0300";

    @Autowired DecisionService decisionService;
    @Autowired OverrideService overrideService;
    @Autowired DecisionRepository decisionRepository;
    @Autowired TestClock clock;

    @Test
    void overrideAppliedBeforeExpiry_revertsAtAndAfterExpiry() {
        Instant t0 = clock.instant();
        Instant expiresAt = t0.plus(Duration.ofHours(1));

        overrideService.create(SNAPSHOT, "值班长-张三", "上游泥石流风险加剧，提前转移",
                DecisionLevel.RED, expiresAt);

        Decision before = decisionService.recompute(SNAPSHOT);
        assertThat(before.getLevel()).isEqualTo(DecisionLevel.RED);
        assertThat(before.getComputedLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(before.getActiveOverrideId()).isNotNull();
        assertThat(before.getEvidenceVersion()).isEqualTo(SNAPSHOT);

        clock.setInstant(expiresAt);
        Decision atExpiry = decisionService.recompute(SNAPSHOT);
        assertThat(atExpiry.getLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(atExpiry.getActiveOverrideId()).isNull();

        clock.setInstant(expiresAt.plusMillis(1));
        Decision afterExpiry = decisionService.recompute(SNAPSHOT);
        assertThat(afterExpiry.getLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(afterExpiry.getActiveOverrideId()).isNull();

        List<Decision> history = decisionRepository.findBySnapshotIdOrderBySequenceNoDesc(SNAPSHOT);
        assertThat(history).hasSizeGreaterThanOrEqualTo(3);
        Decision oldest = history.get(history.size() - 1);
        assertThat(oldest.getSequenceNo()).isEqualTo(before.getSequenceNo());
        assertThat(oldest.getLevel()).isEqualTo(DecisionLevel.RED);
        assertThat(oldest.getActiveOverrideId()).isNotNull();
        assertThat(oldest.getRationale()).contains("人工覆写生效");

        assertThat(history).extracting(Decision::getSequenceNo)
                .contains(before.getSequenceNo(), atExpiry.getSequenceNo(), afterExpiry.getSequenceNo());
    }
}
