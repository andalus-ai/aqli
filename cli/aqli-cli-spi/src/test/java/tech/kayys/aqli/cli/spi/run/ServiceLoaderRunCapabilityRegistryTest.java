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

    @Test
    void snapshotsTypedCapabilityOptions() {
        class TextCapability implements RunCapability, RunCapabilityOptions<TextOptions> {
            @Override
            public String id() {
                return "text";
            }

            @Override
            public void registerOptions(CommandSpec commandSpec) {
            }

            @Override
            public Class<TextOptions> optionsType() {
                return TextOptions.class;
            }

            @Override
            public TextOptions snapshot(ParseResult parseResult) {
                return new TextOptions(128);
            }
        }

        RunCapabilityRegistry registry = ServiceLoaderRunCapabilityRegistry.fromProviders(
                List.of(provider("text", new TextCapability())));
        ParseResult parseResult = new picocli.CommandLine(CommandSpec.create()).parseArgs();

        assertEquals(new TextOptions(128),
                registry.snapshot("text", parseResult, TextOptions.class).orElseThrow());
    }

    @Test
    void returnsEmptyWhenCapabilityDoesNotContributeOptions() {
        RunCapabilityRegistry registry = ServiceLoaderRunCapabilityRegistry.fromProviders(
                List.of(provider("text", capability("text"))));
        ParseResult parseResult = new picocli.CommandLine(CommandSpec.create()).parseArgs();

        assertTrue(registry.snapshot("text", parseResult, TextOptions.class).isEmpty());
        assertTrue(registry.snapshot("unknown", parseResult, TextOptions.class).isEmpty());
    }

    @Test
    void rejectsMismatchedRequestedOptionsType() {
        class TextCapability implements RunCapability, RunCapabilityOptions<TextOptions> {
            @Override
            public String id() {
                return "text";
            }

            @Override
            public void registerOptions(CommandSpec commandSpec) {
            }

            @Override
            public Class<TextOptions> optionsType() {
                return TextOptions.class;
            }

            @Override
            public TextOptions snapshot(ParseResult parseResult) {
                return new TextOptions(128);
            }
        }

        RunCapabilityRegistry registry = ServiceLoaderRunCapabilityRegistry.fromProviders(
                List.of(provider("text", new TextCapability())));
        ParseResult parseResult = new picocli.CommandLine(CommandSpec.create()).parseArgs();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.snapshot("text", parseResult, String.class));
        assertTrue(error.getMessage().contains(TextOptions.class.getName()));
    }

    @Test
    void rejectsNullOptionsSnapshot() {
        class TextCapability implements RunCapability, RunCapabilityOptions<TextOptions> {
            @Override
            public String id() {
                return "text";
            }

            @Override
            public void registerOptions(CommandSpec commandSpec) {
            }

            @Override
            public Class<TextOptions> optionsType() {
                return TextOptions.class;
            }

            @Override
            public TextOptions snapshot(ParseResult parseResult) {
                return null;
            }
        }

        RunCapabilityRegistry registry = ServiceLoaderRunCapabilityRegistry.fromProviders(
                List.of(provider("text", new TextCapability())));
        ParseResult parseResult = new picocli.CommandLine(CommandSpec.create()).parseArgs();

        assertThrows(NullPointerException.class,
                () -> registry.snapshot("text", parseResult, TextOptions.class));
    }

    private record TextOptions(int maxTokens) {
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
