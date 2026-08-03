package gov.basin.evac.domain.notification;

/** Raised when an outbound notification cannot be delivered; signals the dispatcher to retry. */
public class NotificationSendException extends Exception {

    public NotificationSendException(String message) {
        super(message);
    }

    public NotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
