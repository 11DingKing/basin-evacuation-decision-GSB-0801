package cn.gov.basin.evacuation.override;

public class DuplicateRequestIdException extends RuntimeException {

    private final Long existingOverrideId;

    public DuplicateRequestIdException(String requestId, Long existingOverrideId) {
        super("Override with requestId '" + requestId + "' already exists (id=" + existingOverrideId + ")");
        this.existingOverrideId = existingOverrideId;
    }

    public Long getExistingOverrideId() {
        return existingOverrideId;
    }
}
