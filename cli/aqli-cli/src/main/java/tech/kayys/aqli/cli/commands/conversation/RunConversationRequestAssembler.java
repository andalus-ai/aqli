package tech.kayys.aqli.cli.commands.conversation;

import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Assembles the CLI conversation/session portion of an inference request.
 *
 * <p>This class preserves CLI message ordering and legacy prompt behavior.
 * The transport-neutral SDK conversation contract should become the shared
 * source of truth for CLI, REST, and gRPC.</p>
 */
public final class RunConversationRequestAssembler {

    private RunConversationRequestAssembler() {
    }

    public static void apply(
            InferenceRequest.Builder request,
            String sessionId,
            String systemPrompt,
            String retrievalSystemPrompt,
            String userPrompt) {
        if (request == null) {
            throw new IllegalArgumentException("inference request builder is required");
        }

        if (sessionId != null && !sessionId.isBlank()) {
            request.sessionId(sessionId.trim());
        }
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            request.message(Message.system(systemPrompt));
        }
        if (retrievalSystemPrompt != null && !retrievalSystemPrompt.isBlank()) {
            request.message(Message.system(retrievalSystemPrompt));
        }
        request.message(Message.user(userPrompt));
    }
}
