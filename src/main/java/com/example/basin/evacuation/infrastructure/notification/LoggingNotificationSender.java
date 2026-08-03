package com.example.basin.evacuation.infrastructure.notification;

import com.example.basin.evacuation.domain.notification.NotificationOutbox;
import com.example.basin.evacuation.domain.notification.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationOutbox outbox) {
        log.info("Delivering notification id={} channel={} snapshot={} level={}",
                outbox.getId(), outbox.getChannel(), outbox.getSnapshotId(),
                outbox.getPayload().level());
    }
}
