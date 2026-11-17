package tech.kayys.aqli.cli.spi.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

class ServiceLoaderRunCapabilityRegistryTest {

    @Test
    void ordersCapabilitiesDeterministicallyAndFindsById() {
        RunCapability audio = capability("audio");
        RunCapability text = capability("text");
        RunCapabilityRegistry registry = ServiceLoaderRunCapabilityRegistry.fromProviders(List.of(
                provider("text", text),
                provider("audio", audio)));

        assertEquals(List.of("audio", "text"),
                registry.capabilities().stream().map(RunCapability::id).toList());
        assertTrue(registry.find("text").isPresent());
        assertFalse(registry.find("video").isPresent());
    }

    @Test
    void rejectsDuplicateCapabilityIds() {
        RunCapabilityRegistry registry = null;
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ServiceLoaderRunCapabilityRegistry.fromProviders(List.of(
                        provider("text", capability("text")),
                        provider("text", capability("text")))));
        assertTrue(error.getMessage().contains("Duplicate run capability id"));
    }

    @Test
    void rejectsProviderAndCapabilityIdMismatch() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ServiceLoaderRunCapabilityRegistry.fromProviders(
                        List.of(provider("text", capability("audio")))));
        assertTrue(error.getMessage().contains("does not match"));
    }

    private static RunCapabilityProvider provider(String id, RunCapability capability) {
        return new RunCapabilityProvider() {
            @Override
            public String capabilityId() {
                return id;
            }

            @Override
            public RunCapability create() {
                return capability;
            }
        };
    }

    private static RunCapability capability(String id) {
        return new RunCapability() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void registerOptions(CommandSpec commandSpec) {
            }

            @Override
            public void validate(ParseResult parseResult) {
            }
        };
    }
}
