package com.basin.evacuation.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(OutboxMessage message) {
        log.info("发送流域转移通知 channel={} decisionId={} payload={}",
                message.getChannel(), message.getDecisionId(), message.getPayload());
    }
}
