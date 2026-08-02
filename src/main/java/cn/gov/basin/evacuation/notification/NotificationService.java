package cn.gov.basin.evacuation.notification;

import cn.gov.basin.evacuation.decision.DecisionAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxRepository outboxRepository;
    private final OutboxDueRepository dueRepository;
    private final NotificationSender sender;
    private final Clock clock;
    private final int batchSize;

    public NotificationService(OutboxRepository outboxRepository,
                               OutboxDueRepository dueRepository,
                               NotificationSender sender,
                               Clock clock,
                               @Value("${basin.outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.dueRepository = dueRepository;
        this.sender = sender;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DecisionAdvice advice, Map<String, Object> payload) {
        NotificationOutbox outbox = new NotificationOutbox(
                advice.getId(),
                advice.getRegionCode(),
                "DEFAULT",
                payload,
                clock.instant()
        );
        outboxRepository.save(outbox);
    }

    @Scheduled(fixedDelayString = "${basin.outbox.drain-delay-ms:2000}")
    @Transactional
    public int drain() {
        Instant now = clock.instant();
        List<NotificationOutbox> batch = dueRepository.findDueBatch(now, batchSize);
        for (NotificationOutbox item : batch) {
            processOne(item);
        }
        return batch.size();
    }

    @Transactional
    public int processDue() {
        return drain();
    }

    private void processOne(NotificationOutbox item) {
        item.incrementAttempts();
        try {
            sender.send(item.getChannel(), item.getPayload());
            item.markSent(clock.instant());
            outboxRepository.save(item);
        } catch (NotificationSendException | RuntimeException e) {
            Instant nextRetry = nextBackoff(item.getAttempts());
            item.markFailure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    nextRetry, clock.instant());
            if (item.getAttempts() >= MAX_ATTEMPTS) {
                log.error("outbox id={} exhausted attempts={}", item.getId(), item.getAttempts());
            }
            outboxRepository.save(item);
        }
    }

    private Instant nextBackoff(int attempt) {
        long seconds = (long) Math.min(60, Math.pow(2, attempt));
        return clock.instant().plus(Duration.ofSeconds(seconds));
    }
}
