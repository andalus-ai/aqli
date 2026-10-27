/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.kayys.aljabr.safetensor.generation.GenerationConfig;
import tech.kayys.aljabr.spi.inference.InferenceRequest;

/** Request-mapping logic of the pure-Java safetensors backend; none of it needs a loaded model. */
class DirectSafetensorBackendTest {

    @TempDir
    Path tmp;

    @Test
    void samplingStrategyFollowsTheCliDecisionTable() {
        assertEquals(GenerationConfig.SamplingStrategy.GREEDY, DirectSafetensorBackend.samplingStrategy(0.0, 40, 0.9));
        assertEquals(GenerationConfig.SamplingStrategy.GREEDY, DirectSafetensorBackend.samplingStrategy(0.8, 1, 0.9));
        assertEquals(GenerationConfig.SamplingStrategy.TOP_K_TOP_P, DirectSafetensorBackend.samplingStrategy(0.8, 40, 0.9));
        assertEquals(GenerationConfig.SamplingStrategy.TOP_P, DirectSafetensorBackend.samplingStrategy(0.8, 0, 0.9));
        assertEquals(GenerationConfig.SamplingStrategy.TOP_K, DirectSafetensorBackend.samplingStrategy(0.8, 40, 1.0));
        assertEquals(GenerationConfig.SamplingStrategy.GREEDY, DirectSafetensorBackend.samplingStrategy(0.8, 0, 1.0));
    }

    @Test
    void kvQuantParsingAcceptsTheCliSpellings() {
        assertEquals(GenerationConfig.KvCacheQuantization.INT8, DirectSafetensorBackend.parseKvQuant("INT8"));
        assertEquals(GenerationConfig.KvCacheQuantization.INT4, DirectSafetensorBackend.parseKvQuant("int4"));
        assertEquals(GenerationConfig.KvCacheQuantization.INT4, DirectSafetensorBackend.parseKvQuant("turbo"));
        assertEquals(GenerationConfig.KvCacheQuantization.NONE, DirectSafetensorBackend.parseKvQuant(null));
        assertEquals(GenerationConfig.KvCacheQuantization.NONE, DirectSafetensorBackend.parseKvQuant("fp16"));
    }

    @Test
    void checkpointDirectoryIsUsedAsIs() throws Exception {
        Path dir = Files.createDirectory(tmp.resolve("model"));
        assertEquals(dir.toAbsolutePath().normalize(), DirectSafetensorBackend.resolveCheckpointPath(dir.toString()));
    }

    @Test
    void safetensorsFileResolvesToItsDirectory() throws Exception {
        Path dir = Files.createDirectory(tmp.resolve("model"));
        Path weights = Files.writeString(dir.resolve("model.safetensors"), "x");
        assertEquals(dir.toAbsolutePath().normalize(), DirectSafetensorBackend.resolveCheckpointPath(weights.toString()));
    }

    @Test
    void nonCheckpointPathsAreRejected() throws Exception {
        Path other = Files.writeString(tmp.resolve("notes.txt"), "x");
        assertNull(DirectSafetensorBackend.resolveCheckpointPath(other.toString()));
        assertNull(DirectSafetensorBackend.resolveCheckpointPath(tmp.resolve("missing").toString()));
        assertNull(DirectSafetensorBackend.resolveCheckpointPath("   "));
        assertNull(DirectSafetensorBackend.resolveCheckpointPath(null));
        // a model reference such as hf:org/name is not a local checkpoint
        assertNull(DirectSafetensorBackend.resolveCheckpointPath("hf:org/model"));
    }

    @Test
    void inferFailsCleanlyWhenRequestIsNotACheckpoint() {
        DirectSafetensorBackend backend = new DirectSafetensorBackend();
        InferenceRequest request = InferenceRequest.builder()
                .model(tmp.resolve("missing").toString())
                .prompt("hello")
                .build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> backend.infer(request).await().indefinitely());
        assertTrue(e.getMessage().contains("model_path"), e.getMessage());
    }

    @Test
    void streamFailsCleanlyWhenRequestIsNotACheckpoint() {
        DirectSafetensorBackend backend = new DirectSafetensorBackend();
        InferenceRequest request = InferenceRequest.builder()
                .model("hf:org/model")
                .prompt("hello")
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> backend.stream(request).collect().asList().await().indefinitely());
    }

    @Test
    void reportsUnhealthyWithoutAnEngine() {
        assertFalse(new DirectSafetensorBackend().isHealthy());
    }
}
