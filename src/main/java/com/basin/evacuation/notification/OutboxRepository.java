package com.basin.evacuation.notification;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByStatusInOrderByCreatedAtAsc(Collection<OutboxStatus> statuses, Limit limit);

    Page<OutboxMessage> findByStatus(OutboxStatus status, Pageable pageable);

    long countByDecisionIdIn(Collection<UUID> decisionIds);
}
