package gov.basin.evac.domain.model;

/**
 * Health of an individual upstream feed as captured in a snapshot.
 * <ul>
 *   <li>OK      – fresh, current-version data received.</li>
 *   <li>TIMEOUT – the feed did not respond in time.</li>
 *   <li>STALE   – a response arrived but carried an outdated version.</li>
 *   <li>ERROR   – the feed returned an error.</li>
 * </ul>
 * Any status other than OK for a required feed contributes to an INSUFFICIENT_DATA outcome.
 */
public enum UpstreamStatus {
    OK,
    TIMEOUT,
    STALE,
    ERROR;

    public boolean isHealthy() {
        return this == OK;
    }
}
