package cn.gov.basin.evacuation.decision;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class AdvisoryLockRepository {

    @PersistenceContext
    private EntityManager em;

    public void acquireSnapshotLock(String snapshotId) {
        long key = snapshotKey(snapshotId);
        em.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", key)
                .getSingleResult();
    }

    static long snapshotKey(String snapshotId) {
        long h = 1125899906842597L;
        for (int i = 0; i < snapshotId.length(); i++) {
            h = 31 * h + snapshotId.charAt(i);
        }
        return h;
    }
}
