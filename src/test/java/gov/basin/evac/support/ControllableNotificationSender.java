package gov.basin.evac.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import gov.basin.evac.domain.notification.NotificationSendException;
import gov.basin.evac.domain.notification.NotificationSender;

/**
 * Test double for the outbound channel. It can be flipped to fail on demand to exercise the
 * outbox replay path, and records every successfully delivered payload.
 */
public class ControllableNotificationSender implements NotificationSender {

    private final AtomicBoolean failing = new AtomicBoolean(false);
    private final List<String> delivered = new CopyOnWriteArrayList<>();

    public void failNext(boolean fail) {
        failing.set(fail);
    }

    public List<String> delivered() {
        return delivered;
    }

    @Override
    public void send(String payload) throws NotificationSendException {
        if (failing.get()) {
            throw new NotificationSendException("simulated downstream failure");
        }
        delivered.add(payload);
    }
}
