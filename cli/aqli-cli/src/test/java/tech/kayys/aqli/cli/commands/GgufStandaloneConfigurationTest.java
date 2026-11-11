package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GgufStandaloneConfigurationTest {

    @Test
    void systemPropertyTakesPrecedenceOverEnvironment() {
        GgufStandaloneConfiguration configuration = new GgufStandaloneConfiguration(
                Map.of("aqli.gguf.fast_run.threads", "12")::get,
                Map.of("AQLI_GGUF_FAST_THREADS", "6")::get);

        assertEquals("12", configuration.value("aqli.gguf.fast_run.threads"));
        assertEquals(12, configuration.intValue("aqli.gguf.fast_run.threads", 4));
    }

    @Test
    void resolvesCanonicalAndLegacyEnvironmentNamesInOrder() {
        GgufStandaloneConfiguration configuration = new GgufStandaloneConfiguration(
                ignored -> null,
                Map.of("GGUF_THREADS", "8")::get);

        assertEquals(
                java.util.List.of("AQLI_GGUF_FAST_THREADS", "GGUF_THREADS"),
                GgufStandaloneConfiguration.environmentNames("aqli.gguf.fast_run.threads"));
        assertEquals("8", configuration.value("aqli.gguf.fast_run.threads"));
    }

    @Test
    void ignoresBlankValuesAndUsesFallbackForInvalidTypedValues() {
        GgufStandaloneConfiguration configuration = new GgufStandaloneConfiguration(
                ignored -> " ",
                Map.of("AQLI_GGUF_FAST_DEBUG", "perhaps",
                        "AQLI_GGUF_FAST_THREADS", "many",
                        "AQLI_GGUF_FAST_TIMEOUT", "invalid")::get);

        assertFalse(configuration.booleanValue("aqli.gguf.fast_run.debug", false));
        assertEquals(7, configuration.intValue("aqli.gguf.fast_run.threads", 7));
        assertEquals(90L, configuration.longValue("aqli.gguf.fast_run.timeout", 90L));
    }

    @Test
    void supportsBooleanAliasesAndPositiveIntegerClamping() {
        GgufStandaloneConfiguration configuration = new GgufStandaloneConfiguration(
                ignored -> null,
                Map.of("AQLI_GGUF_FAST_DAEMON", "yes",
                        "AQLI_GGUF_FAST_CONTEXT", "-5")::get);

        assertTrue(configuration.booleanValue("aqli.gguf.fast_run.daemon", false));
        assertEquals(1, configuration.positiveInt("aqli.gguf.fast_run.context", 512));
        assertEquals(1, GgufStandaloneConfiguration.parsePositiveInt("0", 8));
    }
}
