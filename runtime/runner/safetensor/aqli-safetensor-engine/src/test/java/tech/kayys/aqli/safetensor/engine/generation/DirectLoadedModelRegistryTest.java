/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation;

import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.safetensor.spi.SafetensorEngine;
import tech.kayys.aljabr.spi.model.ModelConfig;
import tech.kayys.aqli.tokenizer.spi.Tokenizer;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectLoadedModelRegistryTest {

    @Test
    void registersAndFindsByNormalizedPathAndKey() {
        DirectLoadedModelRegistry<FakeLoadedModel> registry = new DirectLoadedModelRegistry<>();
        Path path = Path.of("/tmp/aqli/../aqli/model");
        FakeLoadedModel model = new FakeLoadedModel(path, "model-key");

        registry.register(path, model);

        assertSame(model, registry.find(Path.of("/tmp/aqli/model")));
        assertSame(model, registry.findByKey("model-key"));
        assertTrue(registry.contains(Path.of("/tmp/aqli/model")));
    }

    @Test
    void removesPathAndKeyTogether() {
        DirectLoadedModelRegistry<FakeLoadedModel> registry = new DirectLoadedModelRegistry<>();
        Path path = Path.of("/tmp/aqli/model");
        FakeLoadedModel model = new FakeLoadedModel(path, "model-key");
        registry.register(path, model);

        assertSame(model, registry.remove(path));

        assertFalse(registry.contains(path));
        assertNull(registry.findByKey("model-key"));
    }

    @Test
    void usesStableLockForEquivalentPaths() {
        DirectLoadedModelRegistry<FakeLoadedModel> registry = new DirectLoadedModelRegistry<>();

        assertSame(
                registry.lockFor(Path.of("/tmp/aqli/../aqli/model")),
                registry.lockFor(Path.of("/tmp/aqli/model")));
    }

    @Test
    void snapshotIsDetachedAndUnmodifiable() {
        DirectLoadedModelRegistry<FakeLoadedModel> registry = new DirectLoadedModelRegistry<>();
        FakeLoadedModel model = new FakeLoadedModel(Path.of("/tmp/aqli/model"), "model-key");
        registry.register(model.path(), model);

        Collection<FakeLoadedModel> snapshot = registry.snapshot();
        registry.remove(model.path());

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    private record FakeLoadedModel(Path path, String key) implements SafetensorEngine.LoadedModel {
        @Override
        public Map<String, ?> weights() {
            return Map.of();
        }

        @Override
        public Tokenizer tokenizer() {
            return null;
        }

        @Override
        public boolean isQuantized() {
            return false;
        }

        @Override
        public ModelConfig config() {
            return new ModelConfig();
        }
    }
}
