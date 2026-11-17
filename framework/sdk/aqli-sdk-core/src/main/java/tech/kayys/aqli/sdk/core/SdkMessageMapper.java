package tech.kayys.aqli.sdk.core;

import tech.kayys.aqli.sdk.capability.text.SdkMessage;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.tool.ToolCall;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.util.List;
import java.util.Objects;

/** Converts SDK-owned conversation messages to the legacy execution SPI. */
public final class SdkMessageMapper {

    private SdkMessageMapper() {
    }

    public static Message toExecutionMessage(SdkMessage message) {
        Objects.requireNonNull(message, "message");
        List<Message.ContentPart> parts = message.contentParts().stream()
                .map(SdkMessageMapper::toExecutionContentPart)
                .toList();
        List<ToolCall> toolCalls = message.toolCalls().stream()
                .map(SdkMessageMapper::toExecutionToolCall)
                .toList();

        return new Message(
                Message.Role.valueOf(message.role().name()),
                message.content(),
                parts.isEmpty() ? null : parts,
                message.name(),
                toolCalls.isEmpty() ? null : toolCalls,
                message.toolCallId());
    }

    public static List<Message> toExecutionMessages(List<SdkMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        return messages.stream().map(SdkMessageMapper::toExecutionMessage).toList();
    }

    private static Message.ContentPart toExecutionContentPart(SdkMessage.ContentPart part) {
        return new Message.ContentPart(
                Message.ContentType.valueOf(part.type().name()),
                part.text(),
                part.url(),
                part.data(),
                part.mimeType());
    }

    private static ToolCall toExecutionToolCall(SdkMessage.ToolCall call) {
        return new ToolCall(
                call.id(),
                call.name(),
                new ToolCall.Function(call.function().name(), call.function().arguments()),
                call.arguments(),
                ToolDefinition.Type.valueOf(call.type().name()));
    }
}
