package tech.kayys.aqli.spi.inference;

import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.spi.inference.ToolCallProtocolDescriptor;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.compat.AljabrInferenceDtoAdapter;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallProtocolRequestAdapterTest {

    @Test
    void protocolIsOptionalAndSurvivesBuilderCopy() {
        var request = InferenceRequest.builder()
                .model("example-model")
                .message(Message.user("hello"))
                .build();
        assertNull(request.getToolCallProtocol());

        var protocol = ToolCallProtocolDescriptor.of("functiongemma-native");
        var configured = request.toBuilder().toolCallProtocol(protocol).build();

        assertEquals(protocol, configured.getToolCallProtocol());
        assertEquals(protocol, configured.toBuilder().build().getToolCallProtocol());
    }

    @Test
    void compatibilityAdapterPreservesProtocolInBothDirections() {
        var protocol = ToolCallProtocolDescriptor.of("functiongemma-native");
        var request = InferenceRequest.builder()
                .model("example-model")
                .message(Message.user("hello"))
                .toolCallProtocol(protocol)
                .build();

        var aljabr = AljabrInferenceDtoAdapter.toAljabr(request);
        assertEquals(protocol, aljabr.getToolCallProtocol());

        var roundTrip = AljabrInferenceDtoAdapter.fromAljabr(aljabr);
        assertEquals(protocol, roundTrip.getToolCallProtocol());
    }
}
