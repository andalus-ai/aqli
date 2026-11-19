package tech.kayys.aqli.sdk.core.observability;

/**
 * Interface for clients to hook into SDK-level audit trailing.
 */
public interface SdkAuditLogger {
    void logClientRequestStart(String requestId, String model);
    void logClientRequestComplete(String requestId, String model, int outputTokens, long durationMs);
    void logClientRequestFailure(String requestId, String model, String errorType);

    /**
     * Records consumer-initiated cancellation of a streaming request.
     * Kept default for source and binary compatibility with existing audit integrations.
     */
    default void logClientRequestCancelled(String requestId, String model, long durationMs) {}
}
