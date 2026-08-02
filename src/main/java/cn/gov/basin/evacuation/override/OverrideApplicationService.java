package cn.gov.basin.evacuation.override;

import cn.gov.basin.evacuation.decision.DecisionAdvice;
import cn.gov.basin.evacuation.decision.DecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OverrideApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OverrideApplicationService.class);

    private final OverrideService overrideService;
    private final DecisionService decisionService;
    private final OverrideApplicationService self;

    public OverrideApplicationService(OverrideService overrideService,
                                      DecisionService decisionService,
                                      @Lazy OverrideApplicationService self) {
        this.overrideService = overrideService;
        this.decisionService = decisionService;
        this.self = self;
    }

    public OverrideApplicationResult apply(CreateOverrideCommand cmd) {
        validate(cmd);

        var existing = overrideService.findByRequestId(cmd.requestId());
        if (existing.isPresent()) {
            return buildFromExisting(existing.get(), cmd.requestId());
        }

        try {
            return self.createAndCompute(cmd);
        } catch (DataIntegrityViolationException | DuplicateRequestIdException e) {
            log.info("concurrent duplicate requestId={} detected, re-reading", cmd.requestId());
            ManualOverride dup = overrideService.findByRequestIdInNewTx(cmd.requestId())
                    .orElseThrow(() -> new IllegalStateException(
                            "requestId raced away: " + cmd.requestId()));
            return buildFromExisting(dup, cmd.requestId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OverrideApplicationResult createAndCompute(CreateOverrideCommand cmd) {
        ManualOverride created = overrideService.create(cmd);
        DecisionAdvice advice = decisionService.computeForSnapshot(
                cmd.snapshotId(), cmd.requestId());
        return new OverrideApplicationResult(created, advice, true);
    }

    private OverrideApplicationResult buildFromExisting(ManualOverride override, String requestId) {
        DecisionAdvice advice = decisionService
                .findLatestForSnapshotAndRequest(override.getSnapshotId(), requestId)
                .orElseGet(() -> decisionService.computeForSnapshot(
                        override.getSnapshotId(), requestId));
        return new OverrideApplicationResult(override, advice, false);
    }

    private void validate(CreateOverrideCommand cmd) {
        if (cmd.snapshotId() == null || cmd.snapshotId().isBlank()) {
            throw new IllegalArgumentException("snapshotId is required for an idempotent override");
        }
        if (cmd.requestId() == null || cmd.requestId().isBlank()) {
            throw new IllegalArgumentException("requestId is required for idempotency");
        }
    }
}
