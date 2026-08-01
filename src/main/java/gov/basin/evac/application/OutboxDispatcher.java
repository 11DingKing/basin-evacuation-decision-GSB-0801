package gov.basin.evac.application;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import gov.basin.evac.domain.entity.NotificationOutbox;
import gov.basin.evac.domain.repository.NotificationOutboxRepository;

/**
 * Polls for undelivered outbox records and delegates each to {@link OutboxDelivery}, which runs
 * in its own transaction. Re-invoking {@link #dispatchPending()} retries any non-SENT record —
 * this is the "replay" path that must survive send failures and crashes without duplicating or
 * losing notifications.
 */
@Service
public class OutboxDispatcher {

    private final NotificationOutboxRepository outboxRepository;
    private final OutboxDelivery delivery;

    public OutboxDispatcher(NotificationOutboxRepository outboxRepository,
                            OutboxDelivery delivery) {
        this.outboxRepository = outboxRepository;
        this.delivery = delivery;
    }

    /** Poll for undelivered records and attempt delivery. Returns how many were delivered. */
    @Scheduled(fixedDelayString = "${basin.outbox.poll-ms:5000}")
    public int dispatchPending() {
        List<NotificationOutbox> batch = outboxRepository.findUndelivered(PageRequest.of(0, 50));
        int delivered = 0;
        for (NotificationOutbox record : batch) {
            if (delivery.deliverOne(record.getId())) {
                delivered++;
            }
        }
        return delivered;
    }
}
