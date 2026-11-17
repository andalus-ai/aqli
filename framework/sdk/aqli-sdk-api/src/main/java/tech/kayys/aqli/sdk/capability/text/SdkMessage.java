package tech.kayys.aqli.sdk.capability.text;

import java.util.List;
import java.util.Objects;

/**
 * SDK-owned conversation message. This type deliberately does not expose the
 * execution SPI message or tool-call classes.
 */
public record SdkMessage(
        Role role,
        String content,
        List<ContentPart> contentParts,
        String name,
        List<ToolCall> toolCalls,
        String toolCallId) {

    public SdkMessage {
        Objects.requireNonNull(role, "role");
        contentParts = contentParts == null ? List.of() : List.copyOf(contentParts);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        if (content == null && contentParts.isEmpty() && toolCalls.isEmpty()) {
            throw new IllegalArgumentException("message must contain content, content parts, or tool calls");
        }
    }

    public SdkMessage(Role role, String content) {
        this(role, Objects.requireNonNull(content, "content"), List.of(), null, List.of(), null);
    }

    public static SdkMessage system(String content) {
        return new SdkMessage(Role.SYSTEM, content);
    }

    public static SdkMessage user(String content) {
        return new SdkMessage(Role.USER, content);
    }

    public static SdkMessage assistant(String content) {
        return new SdkMessage(Role.ASSISTANT, content);
    }

    public static SdkMessage tool(String toolCallId, String content) {
        return new SdkMessage(Role.TOOL, content, List.of(), null, List.of(),
                Objects.requireNonNull(toolCallId, "toolCallId"));
    }

    public static SdkMessage multimodal(Role role, List<ContentPart> contentParts) {
        return new SdkMessage(role, null, contentParts, null, List.of(), null);
    }

    public enum Role {
        SYSTEM, USER, ASSISTANT, FUNCTION, TOOL
    }

    public enum ContentType {
        TEXT, IMAGE_URL, IMAGE_BASE64, AUDIO_URL, AUDIO_BASE64, DOCUMENT
    }

    public record ContentPart(
            ContentType type,
            String text,
            String url,
            String data,
            String mimeType) {
        public ContentPart {
            Objects.requireNonNull(type, "type");
        }

        public static ContentPart text(String text) {
            return new ContentPart(ContentType.TEXT, Objects.requireNonNull(text, "text"), null, null, null);
        }

        public static ContentPart imageUrl(String url) {
            return new ContentPart(ContentType.IMAGE_URL, null, Objects.requireNonNull(url, "url"), null, "image/*");
        }

        public static ContentPart imageBase64(String data, String mimeType) {
            return new ContentPart(ContentType.IMAGE_BASE64, null, null,
                    Objects.requireNonNull(data, "data"), mimeType);
        }

        public static ContentPart audioUrl(String url, String mimeType) {
            return new ContentPart(ContentType.AUDIO_URL, null, Objects.requireNonNull(url, "url"), null, mimeType);
        }

        public static ContentPart audioBase64(String data, String mimeType) {
            return new ContentPart(ContentType.AUDIO_BASE64, null, null,
                    Objects.requireNonNull(data, "data"), mimeType);
        }
    }

    public record ToolCall(
            String id,
            String name,
            ToolType type,
            FunctionCall function,
            java.util.Map<String, Object> arguments) {
        public ToolCall {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            type = type == null ? ToolType.FUNCTION : type;
            Objects.requireNonNull(function, "function");
            arguments = arguments == null ? java.util.Map.of()
                    : java.util.Collections.unmodifiableMap(new java.util.HashMap<>(arguments));
        }
    }

    public enum ToolType {
        FUNCTION, MCP_TOOL, CODE_INTERPRETER, FILE_SEARCH
    }

    public record FunctionCall(String name, String arguments) {
        public FunctionCall {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(arguments, "arguments");
        }
    }
}
