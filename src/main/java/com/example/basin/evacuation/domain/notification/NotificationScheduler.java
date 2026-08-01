package com.example.basin.evacuation.domain.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationRelay relay;

    public NotificationScheduler(NotificationRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${app.notification.outbox.relay-interval-ms:5000}")
    public void scheduledDispatch() {
        try {
            relay.dispatchPending();
        } catch (Exception ex) {
            log.error("Scheduled notification dispatch failed", ex);
        }
    }
}
