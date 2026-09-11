package hn.alturaforge.mercadox.appointments.google.oauth;

public class GoogleCalendarAuthorizationException extends RuntimeException {

    public enum Reason {
        INVALID_RETURN_PATH,
        CONNECTION_ALREADY_ACTIVE,
        STATE_STORE_UNAVAILABLE
    }

    private final Reason reason;

    public GoogleCalendarAuthorizationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GoogleCalendarAuthorizationException(Reason reason,
                                                String message,
                                                Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
