package tech.kayys.aqli.spi.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class RequestContextTest {

    @Test
    void preservesUnknownPreferredDeviceAsAnOpaqueHint() {
        RequestContext context = new RequestContext() {
            @Override
            public String apiKey() {
                return "test";
            }

            @Override
            public Optional<String> getPreferredDevice() {
                return Optional.of("vulkan");
            }
        };

        assertEquals(Optional.of("vulkan"), context.getPreferredDevice());
        assertTrue(context.preferredDevice().isEmpty());
    }

    @Test
    void resolvesKnownPreferredDeviceCaseInsensitively() {
        RequestContext context = new RequestContext() {
            @Override
            public String apiKey() {
                return "test";
            }

            @Override
            public Optional<String> getPreferredDevice() {
                return Optional.of("MeTaL");
            }
        };

        assertEquals("metal", context.preferredDevice().orElseThrow().getId());
    }
}
