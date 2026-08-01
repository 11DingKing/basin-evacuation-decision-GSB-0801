package gov.basin.evac.domain.model;

/**
 * The possible outcomes of the decision service. INSUFFICIENT_DATA is deliberately a
 * first-class outcome, entirely distinct from LOW: "we cannot decide because the evidence
 * is incomplete/stale" must never be presented as "the situation is low risk".
 *
 * <p>The four escalating action levels are LOW, WATCH, MOVE_PREPARE and MOVE_NOW, each with a
 * numeric priority used for sorting and comparison.
 */
public enum DecisionLevel {

    /** Evidence is missing or stale; no risk judgement can be made. Highest operational salience. */
    INSUFFICIENT_DATA(1),
    /** Sufficient evidence and conditions are benign. */
    LOW(0),
    /** Monitor closely. */
    WATCH(2),
    /** Prepare to move vulnerable population. */
    MOVE_PREPARE(3),
    /** Evacuate now. */
    MOVE_NOW(4);

    private final int priority;

    DecisionLevel(int priority) {
        this.priority = priority;
    }

    /** Higher means more operationally urgent. */
    public int priority() {
        return priority;
    }

    /** True for the escalating computed-risk ladder (excludes the INSUFFICIENT_DATA sentinel). */
    public boolean isRiskLevel() {
        return this != INSUFFICIENT_DATA;
    }
}
