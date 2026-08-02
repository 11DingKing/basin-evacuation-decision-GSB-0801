package cn.gov.basin.evacuation.decision;

import cn.gov.basin.evacuation.domain.threshold.EvidenceRef;

import java.util.LinkedHashMap;
import java.util.Map;

final class EvidenceRefMapper {

    private EvidenceRefMapper() {
    }

    static Map<String, Object> toMap(EvidenceRef ref) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("snapshotId", ref.snapshotId());
        map.put("rainfall", versionEntry(ref.rainfallVersion(), ref.rainfallHealth()));
        map.put("waterLevel", versionEntry(ref.waterLevelVersion(), ref.waterLevelHealth()));
        map.put("hazard", versionEntry(ref.hazardVersion(), ref.hazardHealth()));
        map.put("infrastructure", versionEntry(ref.infrastructureVersion(), ref.infrastructureHealth()));
        return map;
    }

    private static Map<String, Object> versionEntry(String version, Enum<?> health) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("version", version);
        entry.put("health", health == null ? null : health.name());
        return entry;
    }
}
