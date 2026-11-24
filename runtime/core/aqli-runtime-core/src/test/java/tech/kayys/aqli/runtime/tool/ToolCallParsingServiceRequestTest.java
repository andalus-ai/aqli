package tech.kayys.aqli.runtime.tool;

import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.core.tool.ToolCallParseResult;
import tech.kayys.aljabr.core.tool.ToolCallParserRegistry;
import tech.kayys.aljabr.spi.inference.ToolCallProtocolDescriptor;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallParsingServiceRequestTest {

    @Test
    void selectsParserFromExplicitInferenceRequestProtocol() {
        var protocol = ToolCallProtocolDescriptor.of("test-protocol");
        var parser = (tech.kayys.aljabr.core.tool.ToolCallParser)
                output -> new ToolCallParseResult(List.of(), List.of());
        var registry = ToolCallParserRegistry.builder().register(protocol, parser).build();
        var service = new ToolCallParsingService(registry);
        var request = InferenceRequest.builder()
                .model("example-model")
                .prompt("hello")
                .toolCallProtocol(protocol)
                .build();

        assertTrue(service.parse(request, "model output").isComplete());
    }

    @Test
    void rejectsRequestsWithoutExplicitProtocol() {
        var service = new ToolCallParsingService(ToolCallParserRegistry.builder().build());
        var request = InferenceRequest.builder()
                .model("example-model")
                .prompt("hello")
                .build();

        assertThrows(IllegalStateException.class, () -> service.parse(request, "model output"));
    }
}
