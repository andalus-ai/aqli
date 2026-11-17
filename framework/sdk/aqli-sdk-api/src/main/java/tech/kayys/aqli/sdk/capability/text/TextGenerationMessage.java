package tech.kayys.aqli.sdk.capability.text;

import java.util.Objects;

/**
 * SDK-owned text conversation message.
 *
 * <p>This value intentionally models text messages only. Image, audio, document,
 * and tool-call payloads belong to their respective capability contracts rather
 * than leaking execution-SPI message types into the text API.</p>
 */
public record TextGenerationMessage(
        Role role,
        String content,
        String name,
        String toolCallId) {

    public TextGenerationMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        if (role != Role.TOOL && toolCallId != null) {
            throw new IllegalArgumentException("toolCallId is only valid for TOOL messages");
        }
    }

    public static TextGenerationMessage system(String content) {
        return new TextGenerationMessage(Role.SYSTEM, content, null, null);
    }

    public static TextGenerationMessage user(String content) {
        return new TextGenerationMessage(Role.USER, content, null, null);
    }

    public static TextGenerationMessage assistant(String content) {
        return new TextGenerationMessage(Role.ASSISTANT, content, null, null);
    }

    public static TextGenerationMessage tool(String toolCallId, String content) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId must not be blank for TOOL messages");
        }
        return new TextGenerationMessage(Role.TOOL, content, null, toolCallId);
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        FUNCTION,
        TOOL
    }
}
