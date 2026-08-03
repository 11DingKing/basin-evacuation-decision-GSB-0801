package cn.gov.basin.evacuation.decision;

public class AdviceNotFoundException extends RuntimeException {
    public AdviceNotFoundException(Long id) {
        super("Decision advice not found: " + id);
    }
}
