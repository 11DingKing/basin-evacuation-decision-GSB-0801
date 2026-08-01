package com.example.basin.evacuation.domain.notification;

import com.example.basin.evacuation.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls the transactional outbox and attempts delivery. Each attempt runs in its
 * own transaction. A delivery failure only flips the outbox row to FAILED (with
 * backoff) and never touches the already-committed decision, so the system
 * remains replayable: the relay simply retries until it succeeds or dead-letters.
 *
 * <p>The {@code @Scheduled} trigger lives in a separate {@link NotificationScheduler}
 * bean so that calling {@link #dispatchPending()} goes through the transactional
 * proxy (avoiding self-invocation).
 */
@Service
public class NotificationRelay {

    private static final Logger log = LoggerFactory.getLogger(NotificationRelay.class);
    static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;
    private final NotificationSender sender;
    private final Clock clock;
    private final int batchSize;

    public NotificationRelay(OutboxRepository outboxRepository,
                             NotificationSender sender,
                             Clock clock,
                             OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.sender = sender;
        this.clock = clock;
        this.batchSize = properties.batchSize();
    }

    @Transactional
    public int dispatchPending() {
        Instant now = clock.instant();
        List<NotificationOutbox> batch = outboxRepository.findBatchForDispatch(
                NotificationStatus.PENDING,
                NotificationStatus.FAILED,
                now,
                PageRequest.of(0, batchSize));

        int dispatched = 0;
        for (NotificationOutbox outbox : batch) {
            try {
                sender.send(outbox);
                outbox.markSent(now);
                dispatched++;
            } catch (Exception ex) {
                int attempts = outbox.getRetryCount() + 1;
                String error = truncate(ex.getMessage());
                if (attempts >= MAX_RETRIES) {
                    outbox.markFailed(null, error + " [dead-letter after " + attempts + " attempts]");
                    log.error("Notification dead-lettered id={} attempts={}", outbox.getId(), attempts, ex);
                } else {
                    Instant next = now.plus(backoff(attempts));
                    outbox.markFailed(next, error);
                    log.warn("Notification failed id={} attempt={} next={}", outbox.getId(), attempts, next, ex);
                }
            }
        }
        return dispatched;
    }

    private Duration backoff(int attempt) {
        long seconds = (long) Math.min(60, Math.pow(2, attempt) * 5);
        return Duration.ofSeconds(seconds);
    }

    private String truncate(String s) {
        if (s == null) return "unknown error";
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
