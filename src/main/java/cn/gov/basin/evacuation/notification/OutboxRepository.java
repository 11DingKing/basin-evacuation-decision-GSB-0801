package cn.gov.basin.evacuation.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    long countByStatus(OutboxStatus status);
}
