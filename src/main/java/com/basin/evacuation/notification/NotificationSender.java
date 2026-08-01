package com.basin.evacuation.notification;

/**
 * 通知发送器：生产实现为日志通道；值班系统/短信网关可实现本接口接入。
 * 发送失败抛出异常即视为投递失败，outbox 置为 FAILED 等待重放。
 */
public interface NotificationSender {

    void send(OutboxMessage message);
}
