package gov.basin.evac.domain.notification;

import org.springframework.stereotype.Component;

import gov.basin.evac.domain.entity.Advisory;
import gov.basin.evac.domain.model.AdvisorySource;

/**
 * Independent notification-generation module. It turns a decided advisory into the message
 * payload that will be enqueued in the outbox. It performs no I/O and no persistence — it only
 * composes text — so it can be unit-tested in isolation and reused by any dispatcher.
 */
@Component
public class NotificationComposer {

    /**
     * Compose the human-facing notification payload for an advisory. The payload always states
     * the cited evidence version so downstream recipients can trace the recommendation.
     */
    public String compose(Advisory advisory) {
        String origin = advisory.getSource() == AdvisorySource.OVERRIDE
                ? "人工覆写(override#" + advisory.getOverrideId() + ")"
                : "系统计算";
        return String.format(
                "【流域转移建议】地区%s | 等级%s(优先级%d) | 影响人群%d人 | 依据证据版本v%d(快照%s) | 来源:%s | %s",
                advisory.getRegionCode(),
                advisory.getDecisionLevel(),
                advisory.getPriority(),
                advisory.getAffectedPersons(),
                advisory.getEvidenceVersion(),
                advisory.getSnapshotId(),
                origin,
                advisory.getRationale());
    }
}
