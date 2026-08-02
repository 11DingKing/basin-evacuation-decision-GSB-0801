package cn.gov.basin.evacuation.domain.threshold;

import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;

public record EvidenceRef(
        String snapshotId,
        String rainfallVersion,
        UpstreamHealth rainfallHealth,
        String waterLevelVersion,
        UpstreamHealth waterLevelHealth,
        String hazardVersion,
        UpstreamHealth hazardHealth,
        String infrastructureVersion,
        UpstreamHealth infrastructureHealth
) {
}
