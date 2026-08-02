package cn.gov.basin.evacuation.notification;

import java.util.Map;

public interface NotificationSender {

    void send(String channel, Map<String, Object> payload) throws NotificationSendException;
}
