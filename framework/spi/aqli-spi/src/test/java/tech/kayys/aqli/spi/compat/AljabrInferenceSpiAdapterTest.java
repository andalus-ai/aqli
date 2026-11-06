package tech.kayys.aqli.spi.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.tool.ToolCall;
import tech.kayys.aqli.spi.tool.ToolDefinition;

class AljabrInferenceSpiAdapterTest {

    @Test
    void roundTripsMultimodalMessageAndToolCall() {
        ToolCall call = ToolCall.builder()
                .id("call-1")
                .name("lookup")
                .argument("query", "weather")
                .build();
        Message message = Message.assistantWithToolCalls("Checking", List.of(call));

        tech.kayys.aljabr.spi.Message canonical = AljabrInferenceSpiAdapter.toAljabr(message);
        Message restored = AljabrInferenceSpiAdapter.fromAljabr(canonical);

        assertEquals(message, restored);
        assertNotSame(message, restored);
    }

    @Test
    void roundTripsToolDefinitionWithoutSharingMutableMaps() {
        ToolDefinition legacy = ToolDefinition.builder()
                .name("lookup")
                .type(ToolDefinition.Type.MCP_TOOL)
                .description("Lookup data")
                .parameter("query", "string")
                .metadata("source", "test")
                .strict(true)
                .build();

        tech.kayys.aljabr.spi.tool.ToolDefinition canonical =
                AljabrInferenceSpiAdapter.toAljabr(legacy);
        ToolDefinition restored = AljabrInferenceSpiAdapter.fromAljabr(canonical);

        assertEquals(legacy.getName(), restored.getName());
        assertEquals(legacy.getType(), restored.getType());
        assertEquals(legacy.getDescription(), restored.getDescription());
        assertEquals(legacy.getParameters(), restored.getParameters());
        assertEquals(legacy.getMetadata(), restored.getMetadata());
        assertEquals(legacy.isStrict(), restored.isStrict());
    }

    @Test
    void roundTripsMultimodalContentParts() {
        Message message = Message.multiModal(
                Message.Role.USER,
                List.of(
                        Message.ContentPart.text("Describe this"),
                        Message.ContentPart.imageUrl("https://example.test/image.png")));

        Message restored = AljabrInferenceSpiAdapter.fromAljabr(
                AljabrInferenceSpiAdapter.toAljabr(message));

        assertEquals(message, restored);
    }
}
