package tech.kayys.aqli.spi.execution;

import tech.kayys.aljabr.error.ErrorCode;

/**
 * Compatibility wrapper for InferenceException in execution package.
 * Extends the canonical SPI exception.
 */
public class InferenceException extends tech.kayys.aqli.spi.exception.InferenceException {

    public InferenceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InferenceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public InferenceException(String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
    }

    public InferenceException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
    }
}
