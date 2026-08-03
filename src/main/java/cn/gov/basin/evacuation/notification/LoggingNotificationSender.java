package cn.gov.basin.evacuation.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    private final AtomicReference<NotificationSender> delegate = new AtomicReference<>(new NoopSender());

    public void setDelegate(NotificationSender sender) {
        this.delegate.set(sender == null ? new NoopSender() : sender);
    }

    public void reset() {
        this.delegate.set(new NoopSender());
    }

    @Override
    public void send(String channel, Map<String, Object> payload) throws NotificationSendException {
        try {
            delegate.get().send(channel, payload);
            log.info("notification sent channel={} payload={}", channel, payload);
        } catch (NotificationSendException e) {
            log.warn("notification failed channel={} error={}", channel, e.getMessage());
            throw e;
        }
    }

    private static final class NoopSender implements NotificationSender {
        @Override
        public void send(String channel, Map<String, Object> payload) {
            log.debug("noop-send channel={} payload={}", channel, payload);
        }
    }
}
