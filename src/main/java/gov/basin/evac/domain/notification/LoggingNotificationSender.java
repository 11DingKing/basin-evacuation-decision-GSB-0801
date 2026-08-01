package gov.basin.evac.domain.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default sender that logs the notification. In a real deployment this would push to SMS / IM /
 * emergency-broadcast gateways. Tests substitute a sender that throws to exercise outbox replay.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(String payload) throws NotificationSendException {
        log.info("Delivering notification: {}", payload);
    }
}
