package tech.kayys.aqli.spi.exception;

import tech.kayys.aljabr.error.ErrorCode;

public class QuotaExceededException extends InferenceException {

    public QuotaExceededException(String message) {
        super(ErrorCode.QUOTA_EXCEEDED, message);
    }

    public QuotaExceededException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
