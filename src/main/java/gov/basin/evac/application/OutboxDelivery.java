package gov.basin.evac.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import gov.basin.evac.domain.entity.NotificationOutbox;
import gov.basin.evac.domain.model.OutboxStatus;
import gov.basin.evac.domain.notification.NotificationSendException;
import gov.basin.evac.domain.notification.NotificationSender;
import gov.basin.evac.domain.repository.NotificationOutboxRepository;

/**
 * Delivers a single outbox record in its OWN transaction. Kept in a separate bean (not the
 * dispatcher) so the {@code REQUIRES_NEW} boundary is honoured via Spring's proxy rather than
 * bypassed by self-invocation. A send failure marks only this record FAILED and leaves it
 * replayable; it never rolls back the advisory that produced it.
 */
@Component
public class OutboxDelivery {

    private static final Logger log = LoggerFactory.getLogger(OutboxDelivery.class);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationSender sender;
    private final Clock clock;

    public OutboxDelivery(NotificationOutboxRepository outboxRepository,
                          NotificationSender sender,
                          Clock clock) {
        this.outboxRepository = outboxRepository;
        this.sender = sender;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliverOne(Long id) {
        NotificationOutbox record = outboxRepository.findById(id).orElse(null);
        if (record == null || record.getStatus() == OutboxStatus.SENT) {
            return false;
        }
        try {
            sender.send(record.getPayload());
            record.markSent(clock.instant());
            outboxRepository.save(record);
            return true;
        } catch (NotificationSendException | RuntimeException ex) {
            log.warn("Notification delivery failed for outbox#{}: {}", id, ex.getMessage());
            record.markFailed(ex.getMessage());
            outboxRepository.save(record);
            return false;
        }
    }
}
