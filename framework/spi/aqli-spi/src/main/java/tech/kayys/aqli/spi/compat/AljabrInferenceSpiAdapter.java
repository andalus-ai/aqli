package tech.kayys.aqli.spi.compat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Explicit conversion boundary between legacy AQLI SPI values and the canonical
 * Aljabr inference SPI values.
 *
 * <p>Keep conversions here while downstream AQLI modules migrate to Aljabr
 * contracts. Runtime services should depend on Aljabr values internally and
 * confine these conversions to compatibility edges.
 */
public final class AljabrInferenceSpiAdapter {

    private AljabrInferenceSpiAdapter() {
    }

    public static tech.kayys.aljabr.spi.Message toAljabr(tech.kayys.aqli.spi.Message value) {
        Objects.requireNonNull(value, "value");
        List<tech.kayys.aljabr.spi.Message.ContentPart> parts = value.getContentParts() == null
                ? null
                : value.getContentParts().stream()
                        .map(AljabrInferenceSpiAdapter::toAljabr)
                        .toList();
        List<tech.kayys.aljabr.spi.tool.ToolCall> calls = value.getToolCalls() == null
                ? null
                : value.getToolCalls().stream()
                        .map(AljabrInferenceSpiAdapter::toAljabr)
                        .toList();
        return new tech.kayys.aljabr.spi.Message(
                tech.kayys.aljabr.spi.Message.Role.valueOf(value.getRole().name()),
                value.getContent(),
                parts,
                value.getName(),
                calls,
                value.getToolCallId());
    }

    public static tech.kayys.aqli.spi.Message fromAljabr(tech.kayys.aljabr.spi.Message value) {
        Objects.requireNonNull(value, "value");
        List<tech.kayys.aqli.spi.Message.ContentPart> parts = value.getContentParts() == null
                ? null
                : value.getContentParts().stream()
                        .map(AljabrInferenceSpiAdapter::fromAljabr)
                        .toList();
        List<tech.kayys.aqli.spi.tool.ToolCall> calls = value.getToolCalls() == null
                ? null
                : value.getToolCalls().stream()
                        .map(AljabrInferenceSpiAdapter::fromAljabr)
                        .toList();
        return new tech.kayys.aqli.spi.Message(
                tech.kayys.aqli.spi.Message.Role.valueOf(value.getRole().name()),
                value.getContent(),
                parts,
                value.getName(),
                calls,
                value.getToolCallId());
    }

    public static tech.kayys.aljabr.spi.Message.ContentPart toAljabr(
            tech.kayys.aqli.spi.Message.ContentPart value) {
        Objects.requireNonNull(value, "value");
        return new tech.kayys.aljabr.spi.Message.ContentPart(
                tech.kayys.aljabr.spi.Message.ContentType.valueOf(value.type().name()),
                value.text(),
                value.url(),
                value.data(),
                value.mimeType());
    }

    public static tech.kayys.aqli.spi.Message.ContentPart fromAljabr(
            tech.kayys.aljabr.spi.Message.ContentPart value) {
        Objects.requireNonNull(value, "value");
        return new tech.kayys.aqli.spi.Message.ContentPart(
                tech.kayys.aqli.spi.Message.ContentType.valueOf(value.type().name()),
                value.text(),
                value.url(),
                value.data(),
                value.mimeType());
    }

    public static tech.kayys.aljabr.spi.tool.ToolDefinition toAljabr(
            tech.kayys.aqli.spi.tool.ToolDefinition value) {
        Objects.requireNonNull(value, "value");
        return new tech.kayys.aljabr.spi.tool.ToolDefinition(
                value.getName(),
                tech.kayys.aljabr.spi.tool.ToolDefinition.Type.valueOf(value.getType().name()),
                value.getDescription().orElse(null),
                value.getParameters(),
                value.isStrict(),
                value.getMetadata());
    }

    public static tech.kayys.aqli.spi.tool.ToolDefinition fromAljabr(
            tech.kayys.aljabr.spi.tool.ToolDefinition value) {
        Objects.requireNonNull(value, "value");
        return new tech.kayys.aqli.spi.tool.ToolDefinition(
                value.getName(),
                tech.kayys.aqli.spi.tool.ToolDefinition.Type.valueOf(value.getType().name()),
                value.getDescription().orElse(null),
                value.getParameters(),
                value.isStrict(),
                value.getMetadata());
    }

    public static tech.kayys.aljabr.spi.tool.ToolCall toAljabr(
            tech.kayys.aqli.spi.tool.ToolCall value) {
        Objects.requireNonNull(value, "value");
        tech.kayys.aqli.spi.tool.ToolCall.Function function =
                new tech.kayys.aljabr.spi.tool.ToolCall.Function(
                        value.getFunction().getName(),
                        value.getFunction().getArguments());
        return new tech.kayys.aljabr.spi.tool.ToolCall(
                value.getId(),
                value.getName(),
                function,
                value.getArguments(),
                tech.kayys.aljabr.spi.tool.ToolDefinition.Type.valueOf(value.getType().name()));
    }

    public static tech.kayys.aqli.spi.tool.ToolCall fromAljabr(
            tech.kayys.aljabr.spi.tool.ToolCall value) {
        Objects.requireNonNull(value, "value");
        tech.kayys.aqli.spi.tool.ToolCall.Function function =
                new tech.kayys.aqli.spi.tool.ToolCall.Function(
                        value.getFunction().getName(),
                        value.getFunction().getArguments());
        return new tech.kayys.aqli.spi.tool.ToolCall(
                value.getId(),
                value.getName(),
                function,
                value.getArguments(),
                tech.kayys.aqli.spi.tool.ToolDefinition.Type.valueOf(value.getType().name()));
    }
}
