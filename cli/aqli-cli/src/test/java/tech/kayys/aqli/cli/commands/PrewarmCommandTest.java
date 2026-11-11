package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrewarmCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void effectiveProviderDefaultsToGgufWhenUnknown() {
        assertEquals("gguf", PrewarmCommand.effectiveProvider("auto", "unknown-model", null));
    }

    @Test
    void effectiveProviderInfersLiteRtModelFile() {
        assertEquals("litert", PrewarmCommand.effectiveProvider("auto", null, "/models/gemma-4-E2B-it.litertlm"));
    }

    @Test
    void effectiveProviderInfersGgufModelFile() {
        assertEquals("gguf", PrewarmCommand.effectiveProvider("auto", null, "/models/model.Q4_K_M.gguf"));
    }

    @Test
    void effectiveProviderInfersSafetensorModelFile() {
        assertEquals("safetensor", PrewarmCommand.effectiveProvider("auto", null, "/models/model.safetensors"));
    }

    @Test
    void effectiveProviderInfersSafetensorModelDirectory() {
        assertEquals("safetensor", PrewarmCommand.effectiveProvider(
                "auto", null, "/Users/example/.aqli/models/safetensors/google/functiongemma-270m-it"));
    }

    @Test
    void effectiveProviderHonorsExplicitProvider() {
        assertEquals("litert", PrewarmCommand.effectiveProvider("litert", "b71c9d", "/models/model.gguf"));
    }

    @Test
    void effectiveProviderHonorsExplicitSafetensorProvider() {
        assertEquals("safetensor", PrewarmCommand.effectiveProvider("safetensor", "b71c9d", "/models/model.gguf"));
    }

    @Test
    void safetensorPrewarmInitializesMetalWhenBackendAuto() {
        assertEquals(true, PrewarmCommand.shouldInitializeMetalForSafetensor(null, false));
    }

    @Test
    void safetensorPrewarmInitializesMetalWhenExplicitMetal() {
        assertEquals(true, PrewarmCommand.shouldInitializeMetalForSafetensor("metal", false));
    }

    @Test
    void safetensorPrewarmSkipsMetalWhenCpuRequested() {
        assertEquals(false, PrewarmCommand.shouldInitializeMetalForSafetensor("metal", true));
        assertEquals(false, PrewarmCommand.shouldInitializeMetalForSafetensor("cpu", false));
    }

    @Test
    void autoPlanSelectsUsableLiteRtLmAndGgufModels() throws Exception {
        Path litertDir = Files.createDirectories(tempDir.resolve("models/blobs/litert-good"));
        Path ggufDir = Files.createDirectories(tempDir.resolve("models/blobs/gguf-good"));
        Files.writeString(litertDir.resolve("gemma-4-E2B-it.litertlm"), "");
        Files.writeString(ggufDir.resolve("model.gguf"), "");
        Path index = tempDir.resolve("models/index.json");
        Files.writeString(index, """
                [ {
                  "id" : "example/stale-tflite",
                  "shortId" : "stale-tflite",
                  "format" : "litert",
                  "runnable" : true,
                  "path" : "%s",
                  "source" : "local"
                }, {
                  "id" : "example/missing-litertlm",
                  "shortId" : "missing-lm",
                  "format" : "litert",
                  "runnable" : true,
                  "path" : "%s",
                  "source" : "local"
                }, {
                  "id" : "example/litert-good",
                  "shortId" : "litert-good",
                  "format" : "litert",
                  "runnable" : true,
                  "path" : "%s",
                  "source" : "local"
                }, {
                  "id" : "example/gguf-good",
                  "shortId" : "gguf-good",
                  "format" : "gguf",
                  "runnable" : true,
                  "path" : "%s",
                  "source" : "local"
                } ]
                """.formatted(
                tempDir.resolve("models/blobs/stale/model.tflite"),
                tempDir.resolve("models/blobs/missing/model.litertlm"),
                litertDir,
                ggufDir.resolve("model.gguf")));

        assertEquals(
                java.util.List.of("litert-good", "gguf-good"),
                PrewarmAutoPlan.select(index, "auto", 2, "litert,gguf"));
    }

    @Test
    void autoPlanCanSelectSingleFormat() throws Exception {
        Path ggufDir = Files.createDirectories(tempDir.resolve("models/blobs/gguf-good"));
        Files.writeString(ggufDir.resolve("model.gguf"), "");
        Path index = tempDir.resolve("models/index.json");
        Files.writeString(index, """
                [ {
                  "id" : "example/gguf-good",
                  "shortId" : "gguf-good",
                  "format" : "gguf",
                  "runnable" : true,
                  "path" : "%s",
                  "source" : "local"
                } ]
                """.formatted(ggufDir.resolve("model.gguf")));

        assertEquals(
                java.util.List.of("gguf-good"),
                PrewarmAutoPlan.select(index, "auto:gguf", 2, "litert,gguf"));
    }
}
