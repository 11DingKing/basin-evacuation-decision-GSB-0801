package gov.basin.evac.application;

/** Thrown when a recompute or query references a snapshot id that does not exist. */
public class SnapshotNotFoundException extends RuntimeException {

    public SnapshotNotFoundException(String snapshotId) {
        super("Risk snapshot not found: " + snapshotId);
    }
}
