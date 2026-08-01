package gov.basin.evac.domain.threshold;

import gov.basin.evac.domain.model.DecisionLevel;

/**
 * The immutable result of the threshold evaluation module: the computed decision level, a
 * human-readable rationale, and the number of persons affected. This is pure domain output
 * with no persistence or transport concerns.
 */
public record DecisionEvaluation(DecisionLevel level, String rationale, int affectedPersons) {
}
