package com.example.basin.evacuation.domain.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from NotificationOutbox n
            where (n.status = :pending
                   or (n.status = :failed
                       and n.nextAttemptAt is not null
                       and n.nextAttemptAt <= :now))
            order by n.createdAt asc
            """)
    List<NotificationOutbox> findBatchForDispatch(@Param("pending") NotificationStatus pending,
                                                  @Param("failed") NotificationStatus failed,
                                                  @Param("now") Instant now,
                                                  Pageable pageable);

    List<NotificationOutbox> findByDecisionIdOrderByIdAsc(Long decisionId);
}
