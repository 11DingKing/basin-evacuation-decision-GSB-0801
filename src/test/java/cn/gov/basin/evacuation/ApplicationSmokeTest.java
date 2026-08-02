package cn.gov.basin.evacuation;

import cn.gov.basin.evacuation.snapshot.RiskSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationSmokeTest {

    @Autowired
    RiskSnapshotRepository snapshotRepository;

    @Test
    void contextLoadsAndFlywaySeededInitialSnapshot() {
        var snap = snapshotRepository.findById("sc-510182-20260729T0300");
        assertThat(snap).isPresent();
        assertThat(snap.get().getVulnerablePopulation()).isEqualTo(286);
    }
}
