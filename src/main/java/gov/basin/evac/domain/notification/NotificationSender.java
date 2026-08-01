package gov.basin.evac.domain.notification;

/**
 * Outbound channel abstraction. The dispatcher calls this to actually deliver a composed
 * notification. Failures throw {@link NotificationSendException}, leaving the outbox record in
 * a replayable FAILED/PENDING state so nothing is lost.
 */
public interface NotificationSender {

    void send(String payload) throws NotificationSendException;
}
