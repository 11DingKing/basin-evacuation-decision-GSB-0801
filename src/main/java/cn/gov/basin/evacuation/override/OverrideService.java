package cn.gov.basin.evacuation.override;

import cn.gov.basin.evacuation.region.RegionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class OverrideService {

    private final OverrideRepository repository;
    private final RegionService regionService;
    private final Clock clock;

    public OverrideService(OverrideRepository repository,
                           RegionService regionService,
                           Clock clock) {
        this.repository = repository;
        this.regionService = regionService;
        this.clock = clock;
    }

    @Transactional
    public ManualOverride create(CreateOverrideCommand cmd) {
        if (cmd.targetLevel() == null) {
            throw new IllegalArgumentException("targetLevel must not be null");
        }
        if (!cmd.expiresAt().isAfter(cmd.effectiveFrom())) {
            throw new IllegalArgumentException("expiresAt must be after effectiveFrom");
        }
        if (cmd.requestId() != null && !cmd.requestId().isBlank()) {
            repository.findByRequestId(cmd.requestId()).ifPresent(existing -> {
                throw new DuplicateRequestIdException(cmd.requestId(), existing.getId());
            });
        }
        regionService.get(cmd.regionCode());
        Instant now = clock.instant();
        ManualOverride override = new ManualOverride(
                cmd.regionCode(),
                cmd.operator(),
                cmd.reason(),
                cmd.targetLevel(),
                OverrideStatus.ACTIVE,
                cmd.effectiveFrom(),
                cmd.expiresAt(),
                now,
                cmd.requestId(),
                cmd.snapshotId()
        );
        return repository.save(override);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ManualOverride> findByRequestId(String requestId) {
        return repository.findByRequestId(requestId);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            readOnly = true)
    public java.util.Optional<ManualOverride> findByRequestIdInNewTx(String requestId) {
        return repository.findByRequestId(requestId);
    }

    @Transactional
    public int expireDueOverrides() {
        return repository.expireDueOverrides(clock.instant());
    }

    @Transactional
    public Optional<ActiveOverride> findActiveOverride(String regionCode) {
        Instant now = clock.instant();
        List<ManualOverride> candidates = repository.findActiveForRegionAt(regionCode, now);
        repository.expireDueOverrides(now);
        return candidates.stream()
                .max(Comparator.comparing(ManualOverride::getCreatedAt)
                        .thenComparing(ManualOverride::getId))
                .map(o -> new ActiveOverride(
                        o.getId(),
                        o.getOperator(),
                        o.getReason(),
                        o.getTargetLevel(),
                        o.getExpiresAt()
                ));
    }

    @Transactional(readOnly = true)
    public List<ManualOverride> history(String regionCode) {
        return repository.findHistoryForRegion(regionCode);
    }

    @Transactional(readOnly = true)
    public ManualOverride get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new OverrideNotFoundException(id));
    }

    public record ActiveOverride(
            Long overrideId,
            String operator,
            String reason,
            cn.gov.basin.evacuation.domain.decision.DecisionLevel targetLevel,
            Instant expiresAt
    ) {
    }
}
