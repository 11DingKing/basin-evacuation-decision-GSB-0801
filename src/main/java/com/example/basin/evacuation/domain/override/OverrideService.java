package com.example.basin.evacuation.domain.override;

import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import com.example.basin.evacuation.domain.snapshot.SnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Independent domain module for manual overrides.
 *
 * <p>Overrides are append-only and never deleted. An override is "active" only
 * while it is the latest override for a snapshot and its {@code expiresAt} is
 * still in the future according to the injected {@link Clock}. Once expired, the
 * computed threshold result is restored automatically on the next recompute.
 *
 * <p>Idempotency: when a {@code requestNo} (external business request number) is
 * supplied, the first call persists the override; any subsequent call with the
 * same {@code requestNo} returns the existing row unchanged. A database unique
 * constraint is the final backstop against concurrent replays.
 */
@Service
public class OverrideService {

    private final OverrideRepository overrideRepository;
    private final SnapshotRepository snapshotRepository;
    private final Clock clock;

    public OverrideService(OverrideRepository overrideRepository,
                           SnapshotRepository snapshotRepository,
                           Clock clock) {
        this.overrideRepository = overrideRepository;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    @Transactional
    public ManualOverride create(String snapshotId,
                                 String requestNo,
                                 String operator,
                                 String reason,
                                 DecisionLevel targetLevel,
                                 Instant expiresAt) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (targetLevel == null || !targetLevel.isRiskLevel()) {
            throw new IllegalArgumentException("targetLevel must be one of BLUE/YELLOW/ORANGE/RED");
        }

        if (requestNo != null && !requestNo.isBlank()) {
            Optional<ManualOverride> existing = overrideRepository.findByRequestNo(requestNo);
            if (existing.isPresent()) {
                ManualOverride found = existing.get();
                if (!found.getSnapshotId().equals(snapshotId)) {
                    throw new IllegalStateException(
                            "requestNo " + requestNo + " already used by snapshot " + found.getSnapshotId());
                }
                return found;
            }
        }

        Instant now = clock.instant();
        Instant effectiveExpiresAt = (expiresAt != null) ? expiresAt : now.plusSeconds(1800);
        if (!effectiveExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }

        RiskSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("snapshot not found: " + snapshotId));

        ManualOverride override = ManualOverride.builder()
                .snapshotId(snapshot.getSnapshotId())
                .operator(operator)
                .reason(reason)
                .targetLevel(targetLevel)
                .requestNo(requestNo)
                .expiresAt(effectiveExpiresAt)
                .createdAt(now)
                .build();
        try {
            return overrideRepository.save(override);
        } catch (DataIntegrityViolationException ex) {
            if (requestNo != null && !requestNo.isBlank()) {
                return overrideRepository.findByRequestNo(requestNo)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Optional<ManualOverride> findActive(String snapshotId) {
        return overrideRepository.findActiveOverride(snapshotId, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ManualOverride> history(String snapshotId) {
        return overrideRepository.findBySnapshotIdOrderByIdDesc(snapshotId);
    }

    @Transactional(readOnly = true)
    public Optional<ManualOverride> findById(Long id) {
        return overrideRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<ManualOverride> findByRequestNo(String requestNo) {
        return overrideRepository.findByRequestNo(requestNo);
    }
}
