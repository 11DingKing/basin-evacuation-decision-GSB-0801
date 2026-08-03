package cn.gov.basin.evacuation.domain.snapshot;

import java.math.BigDecimal;

public interface RiskSnapshotView {
    String getSnapshotId();
    BigDecimal getRainfall3hMm();
    BigDecimal getWaterLevelM();
    HazardStatus getHazardStatus();
    RoadStatus getPrimaryRoadStatus();
    Integer getVulnerablePopulation();

    UpstreamHealth getRainfallHealth();
    UpstreamHealth getWaterLevelHealth();
    UpstreamHealth getHazardHealth();
    UpstreamHealth getInfrastructureHealth();

    String getRainfallVersion();
    String getWaterLevelVersion();
    String getHazardVersion();
    String getInfrastructureVersion();
}
