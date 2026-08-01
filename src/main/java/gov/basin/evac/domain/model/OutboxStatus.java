package gov.basin.evac.domain.model;

/** Delivery status of a notification in the transactional outbox. */
public enum OutboxStatus {
    PENDING, SENT, FAILED
}
