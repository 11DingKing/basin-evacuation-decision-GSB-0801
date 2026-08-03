package cn.gov.basin.evacuation.override;

public class OverrideNotFoundException extends RuntimeException {
    public OverrideNotFoundException(Long id) {
        super("Override not found: " + id);
    }
}
