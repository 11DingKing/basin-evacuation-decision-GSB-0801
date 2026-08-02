package cn.gov.basin.evacuation.notification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class OutboxDueRepository {

    @PersistenceContext
    private EntityManager em;

    public List<NotificationOutbox> findDueBatch(Instant now, int batchSize) {
        return em.createQuery(
                        "select n from NotificationOutbox n "
                                + "where n.status in (cn.gov.basin.evacuation.notification.OutboxStatus.PENDING, "
                                + "                    cn.gov.basin.evacuation.notification.OutboxStatus.FAILED) "
                                + "and n.nextRetryAt <= :now "
                                + "order by n.id asc",
                        NotificationOutbox.class)
                .setParameter("now", now)
                .setFirstResult(0)
                .setMaxResults(batchSize)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", 0)
                .getResultList();
    }
}
