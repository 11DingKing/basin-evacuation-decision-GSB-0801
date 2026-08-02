package cn.gov.basin.evacuation.snapshot;

public class SnapshotNotFoundException extends RuntimeException {
    public SnapshotNotFoundException(String snapshotId) {
        super("Risk snapshot not found: " + snapshotId);
    }
}
