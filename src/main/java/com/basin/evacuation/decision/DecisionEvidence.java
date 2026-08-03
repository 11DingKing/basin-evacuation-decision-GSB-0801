package com.basin.evacuation.decision;

import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RiskSnapshot;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.UpstreamHealth;
import com.basin.evacuation.snapshot.UpstreamKind;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 决策引用的证据快照：每条建议都必须能说清楚引用的是哪个证据版本
 * （snapshotId + snapshotVersion），并冻结当时的全部输入与四类上游健康状态。
 * 人口单位固定为「人」。
 */
public record DecisionEvidence(
        String snapshotId,
        int snapshotVersion,
        String regionCode,
        BigDecimal rainfall3hMm,
        BigDecimal waterLevelM,
        HazardPointStatus hazardPointStatus,
        RoadStatus primaryRoadStatus,
        RoadStatus secondaryRoadStatus,
        int vulnerablePopulation,
        Map<UpstreamKind, UpstreamHealth> upstreamHealth) {

    public static DecisionEvidence from(RiskSnapshot s) {
        return new DecisionEvidence(
                s.getSnapshotId(),
                s.getVersion(),
                s.getRegionCode(),
                s.getRainfall3hMm(),
                s.getWaterLevelM(),
                s.getHazardPointStatus(),
                s.getPrimaryRoadStatus(),
                s.getSecondaryRoadStatus(),
                s.getVulnerablePopulation(),
                s.getUpstreamHealth());
    }
}
