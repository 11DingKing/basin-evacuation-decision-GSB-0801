package gov.basin.evac.domain.override;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import gov.basin.evac.domain.entity.ManualOverride;

/**
 * Independent manual-override module. It knows only how to pick the override that is in force
 * at a given instant; it has no knowledge of thresholds, persistence transactions or transport.
 *
 * <p>An override is effective strictly within [effectiveFrom, expiresAt). This module is what
 * makes overrides "expire automatically": once {@code now} reaches or passes {@code expiresAt}
 * no override is returned, so the orchestrator falls back to the freshly computed result. The
 * injectable clock supplied by callers is what lets tests freeze time before / exactly at /
 * just after expiry.
 */
@Component
public class OverrideResolver {

    /**
     * @param overrides all overrides for a snapshot (order irrelevant; history is never deleted)
     * @param now       the evaluation instant, from the injectable clock
     * @return the override in force at {@code now}, or empty if none applies
     */
    public Optional<ManualOverride> activeOverride(List<ManualOverride> overrides, Instant now) {
        return overrides.stream()
                .filter(o -> o.isActiveAt(now))
                // If multiple overlap, the one with the latest effectiveFrom wins.
                .max((a, b) -> a.getEffectiveFrom().compareTo(b.getEffectiveFrom()));
    }
}
