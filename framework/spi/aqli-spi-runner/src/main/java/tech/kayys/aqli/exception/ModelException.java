package tech.kayys.aqli.exception;

//import tech.kayys.aqli.engine.exception.InferenceException;
import tech.kayys.alkhawarizm.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;

/**
 * Exception thrown for model-related errors in the Aljabr Engine.
 */
public class ModelException extends InferenceException {
    private final String modelId;

    public ModelException(ErrorCode errorCode, String message, String modelId) {
        super(errorCode, message);
        this.modelId = modelId;
    }

    public ModelException(ErrorCode errorCode, String message, String modelId, Throwable cause) {
        super(errorCode, message, cause);
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }
}
