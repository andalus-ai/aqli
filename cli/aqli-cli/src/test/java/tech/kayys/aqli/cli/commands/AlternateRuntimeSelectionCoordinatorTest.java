package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternateRuntimeSelectionCoordinatorTest {

    @Test
    void preservesRouteWhenNoAlternateRuntimeIsAvailable() {
        AlternateRuntimeSelectionCoordinator.Result result =
                AlternateRuntimeSelectionCoordinator.select(
                        new AlternateRuntimeSelectionCoordinator.Options(
                                "cpu", "example-model", null, "safetensors",
                                false, false, null),
                        new CliLocalModelResolver(),
                        provider -> false,
                        runtime -> false);

        assertEquals("cpu", result.providerId());
        assertEquals("example-model", result.modelId());
        assertNull(result.localPath());
        assertEquals("safetensors", result.format());
        assertNull(result.routeReport());
        assertTrue(result.notices().isEmpty());
    }
}
