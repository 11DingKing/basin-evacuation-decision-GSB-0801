package gov.basin.evac.domain.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import gov.basin.evac.domain.entity.NotificationOutbox;
import gov.basin.evac.domain.model.OutboxStatus;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    /** Records awaiting delivery or needing retry, oldest first, for the dispatcher to replay. */
    @Query("select o from NotificationOutbox o where o.status <> gov.basin.evac.domain.model.OutboxStatus.SENT order by o.createdAt asc")
    List<NotificationOutbox> findUndelivered(Pageable pageable);

    long countByStatus(OutboxStatus status);
}
