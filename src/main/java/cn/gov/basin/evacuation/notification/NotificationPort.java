package cn.gov.basin.evacuation.notification;

import cn.gov.basin.evacuation.decision.DecisionAdvice;

import java.util.Map;

public interface NotificationPort {

    void enqueue(DecisionAdvice advice, Map<String, Object> payload);
}
