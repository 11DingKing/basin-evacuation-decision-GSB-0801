package cn.gov.basin.evacuation;

import cn.gov.basin.evacuation.decision.DecisionAdvice;
import cn.gov.basin.evacuation.notification.NotificationPort;
import cn.gov.basin.evacuation.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@TestConfiguration
public class FailingNotificationPortConfig {

    public static final AtomicBoolean FAIL_NEXT_ENQUEUE = new AtomicBoolean(false);

    static class DelegatingPort implements NotificationPort {
        private final NotificationPort delegate;

        DelegatingPort(NotificationPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void enqueue(DecisionAdvice advice, Map<String, Object> payload, String requestId) {
            if (FAIL_NEXT_ENQUEUE.get()) {
                throw new IllegalStateException("simulated enqueue failure for atomicity test");
            }
            delegate.enqueue(advice, payload, requestId);
        }
    }

    @Bean
    @Primary
    public NotificationPort failingNotificationPort(@Autowired NotificationService service) {
        return new DelegatingPort(service);
    }
}
