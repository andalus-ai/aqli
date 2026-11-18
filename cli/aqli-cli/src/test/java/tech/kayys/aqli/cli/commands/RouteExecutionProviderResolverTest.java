package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteExecutionProviderResolverTest {

    @Test
    void preservesNonLibTorchProvider() {
        assertEquals(
                "cpu",
                RouteExecutionProviderResolver.resolve(
                        "cpu", "/models/checkpoint", value -> true, value -> true));
    }

    @Test
    void mapsLibTorchSafetensorsCheckpointToSafetensorsProvider() {
        assertEquals(
                "safetensor",
                RouteExecutionProviderResolver.resolve(
                        "libtorch", "/models/checkpoint", value -> true, value -> false));
    }

    @Test
    void mapsLibTorchSafetensorsWeightFileToSafetensorsProvider() {
        assertEquals(
                "safetensor",
                RouteExecutionProviderResolver.resolve(
                        "LIBTORCH", "/models/model.safetensors", value -> false, value -> true));
    }

    @Test
    void preservesLibTorchForOtherArtifacts() {
        assertEquals(
                "libtorch",
                RouteExecutionProviderResolver.resolve(
                        "libtorch", "/models/model.pt", value -> false, value -> false));
    }

    @Test
    void preservesProviderForNullOrBlankPath() {
        assertEquals(
                "libtorch",
                RouteExecutionProviderResolver.resolve(
                        "libtorch", null, value -> true, value -> true));
        assertEquals(
                "libtorch",
                RouteExecutionProviderResolver.resolve(
                        "libtorch", "  ", value -> true, value -> true));
    }
}
