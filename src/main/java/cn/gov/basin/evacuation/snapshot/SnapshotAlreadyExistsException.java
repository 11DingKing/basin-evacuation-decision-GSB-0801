package cn.gov.basin.evacuation.snapshot;

public class SnapshotAlreadyExistsException extends RuntimeException {
    public SnapshotAlreadyExistsException(String snapshotId) {
        super("Risk snapshot already exists: " + snapshotId);
    }
}
