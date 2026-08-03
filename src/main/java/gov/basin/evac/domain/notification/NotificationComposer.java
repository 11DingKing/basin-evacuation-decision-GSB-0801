package gov.basin.evac.domain.notification;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import gov.basin.evac.domain.entity.Advisory;
import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.model.AdvisorySource;

/**
 * Independent notification-generation module. It turns a decided advisory into the message
 * payload that will be enqueued in the outbox. It performs no I/O and no persistence — it only
 * composes text — so it can be unit-tested in isolation and reused by any dispatcher.
 *
 * <p>Every payload states the snapshot ID, the cited evidence version, the per-feed upstream
 * versions, and (when the advisory came from an override) the business request number, so
 * downstream recipients can fully trace the recommendation.
 */
@Component
public class NotificationComposer {

    /**
     * @param advisory     the decided advisory
     * @param snapshot     the snapshot that produced it (source of upstream versions)
     * @param requestId    the override's business request number, or {@code null} for computed advisories
     */
    public String compose(Advisory advisory, RiskSnapshot snapshot, String requestId) {
        String origin = advisory.getSource() == AdvisorySource.OVERRIDE
                ? "人工覆写(override#" + advisory.getOverrideId() + ")"
                : "系统计算";
        String upstreamVersions = snapshot.getUpstreamHealth().stream()
                .sorted((a, b) -> a.getSource().name().compareTo(b.getSource().name()))
                .map(h -> h.getSource() + "=" + h.getReportedVersion())
                .collect(Collectors.joining(","));
        String requestPart = requestId != null ? requestId : "-";
        return String.format(
                "【流域转移建议】快照%s | 证据版本v%d | 上游版本[%s] | 请求号%s | 地区%s | 等级%s(优先级%d) | 影响人群%d人 | 来源:%s | %s",
                advisory.getSnapshotId(),
                advisory.getEvidenceVersion(),
                upstreamVersions,
                requestPart,
                advisory.getRegionCode(),
                advisory.getDecisionLevel(),
                advisory.getPriority(),
                advisory.getAffectedPersons(),
                origin,
                advisory.getRationale());
    }
}
