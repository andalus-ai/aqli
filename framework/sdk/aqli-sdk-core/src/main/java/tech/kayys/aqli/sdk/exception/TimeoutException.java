package tech.kayys.aqli.sdk.exception;

/**
 * Exception indicating a request timeout.
 */
public class TimeoutException extends RetryableException {

    public TimeoutException(String message) {
        super("TIMEOUT", message);
    }

    public TimeoutException(String message, Throwable cause) {
        super("TIMEOUT", message, cause);
    }
}
