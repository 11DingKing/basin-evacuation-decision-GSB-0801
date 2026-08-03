package cn.gov.basin.evacuation;

import cn.gov.basin.evacuation.domain.snapshot.HazardStatus;
import cn.gov.basin.evacuation.domain.snapshot.RoadStatus;
import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;
import cn.gov.basin.evacuation.snapshot.CreateSnapshotCommand;
import cn.gov.basin.evacuation.snapshot.RiskSnapshot;
import cn.gov.basin.evacuation.snapshot.SnapshotService;

import java.math.BigDecimal;
import java.time.Instant;

public final class TestSnapshots {

    private TestSnapshots() {
    }

    public static RiskSnapshot degraded(SnapshotService service,
                                        String snapshotId,
                                        String regionCode,
                                        UpstreamHealth rainfallHealth,
                                        UpstreamHealth waterHealth,
                                        UpstreamHealth hazardHealth,
                                        UpstreamHealth infraHealth,
                                        HazardStatus hazardStatus,
                                        RoadStatus primary) {
        CreateSnapshotCommand cmd = new CreateSnapshotCommand(
                snapshotId,
                regionCode,
                Instant.parse("2026-07-29T03:00:00Z"),
                new BigDecimal("3.00"),
                new BigDecimal("4.00"),
                hazardStatus,
                primary,
                RoadStatus.UNKNOWN,
                120,
                rainfallHealth,
                waterHealth,
                hazardHealth,
                infraHealth,
                "rain-old",
                "wl-old",
                "haz-old",
                "infra-old"
        );
        return service.ingest(cmd);
    }
}
