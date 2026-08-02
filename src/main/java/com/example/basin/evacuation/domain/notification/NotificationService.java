package com.example.basin.evacuation.domain.notification;

import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Independent domain module responsible for generating notification outbox
 * entries. The entries are written in the SAME transaction as the decision (the
 * outbox pattern), so a decision can never be persisted without its notification
 * record and vice versa. Actual delivery happens asynchronously via the relay.
 */
@Service
public class NotificationService {

    private final OutboxRepository outboxRepository;
    private final Clock clock;

    public NotificationService(OutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional
    public List<NotificationOutbox> createForDecision(Decision decision,
                                                      RiskSnapshot snapshot,
                                                      String requestNo) {
        List<NotificationChannel> channels = channelsFor(decision.getLevel());
        NotificationPayload.UpstreamVersions upstreamVersions =
                NotificationPayload.UpstreamVersions.builder()
                        .rainfall(snapshot.getRainfallVersion())
                        .waterLevel(snapshot.getWaterLevelVersion())
                        .hazard(snapshot.getHazardVersion())
                        .road(snapshot.getRoadVersion())
                        .build();
        NotificationPayload payload = NotificationPayload.builder()
                .snapshotId(decision.getSnapshotId())
                .districtCode(decision.getDistrictCode())
                .decisionId(decision.getId())
                .level(decision.getLevel())
                .sequenceNo(decision.getSequenceNo())
                .vulnerablePopulation(snapshot.getVulnerablePopulation())
                .evidenceVersion(decision.getEvidenceVersion())
                .rationale(decision.getRationale())
                .recommendedActions(actionsFor(decision.getLevel()))
                .requestNo(requestNo)
                .upstreamVersions(upstreamVersions)
                .build();

        List<NotificationOutbox> created = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            NotificationOutbox outbox = NotificationOutbox.builder()
                    .snapshotId(decision.getSnapshotId())
                    .decisionId(decision.getId())
                    .channel(channel)
                    .payload(payload)
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .createdAt(clock.instant())
                    .build();
            created.add(outboxRepository.save(outbox));
        }
        return created;
    }

    private List<NotificationChannel> channelsFor(DecisionLevel level) {
        if (level == DecisionLevel.INSUFFICIENT_DATA) {
            return List.of(NotificationChannel.PLATFORM);
        }
        return switch (level) {
            case RED -> List.of(NotificationChannel.SMS, NotificationChannel.VOICE,
                    NotificationChannel.BROADCAST, NotificationChannel.PLATFORM);
            case ORANGE -> List.of(NotificationChannel.SMS, NotificationChannel.BROADCAST,
                    NotificationChannel.PLATFORM);
            case YELLOW -> List.of(NotificationChannel.SMS, NotificationChannel.PLATFORM);
            case BLUE -> List.of(NotificationChannel.PLATFORM);
            default -> List.of(NotificationChannel.PLATFORM);
        };
    }

    private List<String> actionsFor(DecisionLevel level) {
        return switch (level) {
            case RED -> List.of(
                    "立即组织受威胁区域群众按预定路线转移",
                    "对脆弱人群（老人、儿童、行动不便者）一对一帮扶转移",
                    "封闭危险路段，设置警戒，抢险队伍待命",
                    "每15分钟滚动上报雨水情与转移进度");
            case ORANGE -> List.of(
                    "做好转移准备，提前通知脆弱人群收拾必需品",
                    "检查安置点物资与转移路线，预置抢险力量",
                    "加密监测雨水情与隐患点，每30分钟会商一次",
                    "主路封闭区域提前安排备用路线");
            case YELLOW -> List.of(
                    "加强值班值守，关注雨水情发展",
                    "提醒沿河、陡坡、隐患点附近居民提高警惕",
                    "检查转移路线与安置点可用性");
            case BLUE -> List.of(
                    "保持日常监测与值班",
                    "向相关责任人推送风险提示");
            case INSUFFICIENT_DATA -> List.of(
                    "立即排查上游数据链路并补传缺失数据",
                    "在数据补齐前按最不利情况加强现场巡查",
                    "通知值班领导人工研判，不得自动下发转移指令");
        };
    }
}
