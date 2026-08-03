package com.basin.evacuation.notification;

import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.snapshot.RiskSnapshot;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final OutboxRepository outbox;
    private final NotificationGenerator generator;
    private final Clock clock;

    public NotificationService(OutboxRepository outbox, NotificationGenerator generator, Clock clock) {
        this.outbox = outbox;
        this.generator = generator;
        this.clock = clock;
    }

    /**
     * 把通知写入 outbox。REQUIRED：加入调用方事务，
     * 与决策行同事务提交 —— 要么都写成功，要么整体回滚，不存在写了一半的状态。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxMessage enqueue(Decision decision, RiskSnapshot snapshot) {
        return outbox.save(generator.toOutbox(decision, snapshot, Instant.now(clock)));
    }
}
