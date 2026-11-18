package tech.kayys.aqli.sdk.capability.text;

import java.util.List;
import java.util.Objects;

/** SDK-owned text conversation message with optional assistant tool-call history. */
public record TextGenerationMessage(
        Role role, String content, String name, String toolCallId, List<TextGenerationToolCall> toolCalls) {
    public TextGenerationMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        if (role != Role.TOOL && toolCallId != null) throw new IllegalArgumentException("toolCallId is only valid for TOOL messages");
        if (role != Role.ASSISTANT && !toolCalls.isEmpty()) throw new IllegalArgumentException("toolCalls are only valid for ASSISTANT messages");
    }
    /** Source-compatible constructor for messages without tool calls. */
    public TextGenerationMessage(Role role, String content, String name, String toolCallId) {
        this(role, content, name, toolCallId, List.of());
    }
    public static TextGenerationMessage system(String content) { return new TextGenerationMessage(Role.SYSTEM, content, null, null, List.of()); }
    public static TextGenerationMessage user(String content) { return new TextGenerationMessage(Role.USER, content, null, null, List.of()); }
    public static TextGenerationMessage assistant(String content) { return new TextGenerationMessage(Role.ASSISTANT, content, null, null, List.of()); }
    public static TextGenerationMessage tool(String toolCallId, String content) {
        if (toolCallId == null || toolCallId.isBlank()) throw new IllegalArgumentException("toolCallId must not be blank for TOOL messages");
        return new TextGenerationMessage(Role.TOOL, content, null, toolCallId, List.of());
    }
    public enum Role { SYSTEM, USER, ASSISTANT, FUNCTION, TOOL }
}
