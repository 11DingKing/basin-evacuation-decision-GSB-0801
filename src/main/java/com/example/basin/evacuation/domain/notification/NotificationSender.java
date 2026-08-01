package com.example.basin.evacuation.domain.notification;

/**
 * Outbound port for actually delivering a notification. Implementations talk to
 * SMS / voice / broadcast / platform gateways. The port is isolated so that
 * delivery failures do not affect the decision transaction: the outbox row is
 * marked FAILED and retried later.
 */
public interface NotificationSender {

    void send(NotificationOutbox outbox) throws NotificationDeliveryException;
}
