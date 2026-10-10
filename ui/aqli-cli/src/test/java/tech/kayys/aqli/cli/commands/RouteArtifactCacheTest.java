/*
 * Aqli CLI
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteArtifactCacheTest {
    private static final String CACHE_DIR_PROPERTY = "aqli.test.route_artifact_cache.dir";
    private static final String CACHE_ENABLED_PROPERTY = "aqli.test.route_artifact_cache.enabled";
    private static final String CACHE_FILE = "routes.tsv";

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreProperties() {
        System.clearProperty(CACHE_DIR_PROPERTY);
        System.clearProperty(CACHE_ENABLED_PROPERTY);
    }

    @Test
    void putAndFindRoundTripThroughConfiguredCacheDirectory() throws Exception {
        Path cacheDir = Files.createDirectory(tempDir.resolve("cache"));
        Path artifact = Files.writeString(tempDir.resolve("model.gguf"), "fake");
        System.setProperty(CACHE_DIR_PROPERTY, cacheDir.toString());

        RouteArtifactCache.put(
                CACHE_DIR_PROPERTY,
                CACHE_ENABLED_PROPERTY,
                CACHE_FILE,
                "model-key",
                artifact,
                Files::isRegularFile);

        assertEquals(
                artifact.toAbsolutePath().normalize(),
                RouteArtifactCache.find(
                                CACHE_DIR_PROPERTY,
                                CACHE_ENABLED_PROPERTY,
                                CACHE_FILE,
                                "model-key",
                                Files::isRegularFile)
                        .orElseThrow());
    }

    @Test
    void disabledCacheDoesNotWriteOrReadEntries() throws Exception {
        Path cacheDir = Files.createDirectory(tempDir.resolve("disabled-cache"));
        Path artifact = Files.writeString(tempDir.resolve("model.gguf"), "fake");
        System.setProperty(CACHE_DIR_PROPERTY, cacheDir.toString());
        System.setProperty(CACHE_ENABLED_PROPERTY, "false");

        RouteArtifactCache.put(
                CACHE_DIR_PROPERTY,
                CACHE_ENABLED_PROPERTY,
                CACHE_FILE,
                "model-key",
                artifact,
                Files::isRegularFile);

        assertFalse(Files.exists(cacheDir.resolve(CACHE_FILE)));
        assertTrue(RouteArtifactCache.find(
                        CACHE_DIR_PROPERTY,
                        CACHE_ENABLED_PROPERTY,
                        CACHE_FILE,
                        "model-key",
                        Files::isRegularFile)
                .isEmpty());
    }

    @Test
    void staleCachedArtifactIsIgnored() throws Exception {
        Path cacheDir = Files.createDirectory(tempDir.resolve("stale-cache"));
        Path artifact = Files.writeString(tempDir.resolve("model.gguf"), "fake");
        System.setProperty(CACHE_DIR_PROPERTY, cacheDir.toString());

        RouteArtifactCache.put(
                CACHE_DIR_PROPERTY,
                CACHE_ENABLED_PROPERTY,
                CACHE_FILE,
                "model-key",
                artifact,
                Files::isRegularFile);
        Files.delete(artifact);

        assertTrue(RouteArtifactCache.find(
                        CACHE_DIR_PROPERTY,
                        CACHE_ENABLED_PROPERTY,
                        CACHE_FILE,
                        "model-key",
                        Files::isRegularFile)
                .isEmpty());
    }

    @Test
    void replacingAKeyKeepsOtherEntries() throws Exception {
        Path cacheDir = Files.createDirectory(tempDir.resolve("replace-cache"));
        Path first = Files.writeString(tempDir.resolve("first.gguf"), "first");
        Path second = Files.writeString(tempDir.resolve("second.gguf"), "second");
        Path other = Files.writeString(tempDir.resolve("other.gguf"), "other");
        System.setProperty(CACHE_DIR_PROPERTY, cacheDir.toString());

        RouteArtifactCache.put(
                CACHE_DIR_PROPERTY,
                CACHE_ENABLED_PROPERTY,
                CACHE_FILE,
                "model-key",
                first,
                Files::isRegularFile);
        RouteArtifactCache.put(
                CACHE_DIR_PROPERTY,
                CACHE_ENABLED_PROPERTY,
                CACHE_FILE,
                "other-key",
                other,
                Files::isRegularFile);
        RouteArtifactCache.put(
                CACHE_DIR_PROPERTY,
                CACHE_ENABLED_PROPERTY,
                CACHE_FILE,
                "model-key",
                second,
                Files::isRegularFile);

        List<String> lines = Files.readAllLines(cacheDir.resolve(CACHE_FILE));
        assertEquals(2, lines.size());
        assertFalse(lines.stream().anyMatch(line -> line.contains(first.toAbsolutePath().normalize().toString())));
        assertTrue(lines.stream().anyMatch(line -> line.contains(second.toAbsolutePath().normalize().toString())));
        assertTrue(lines.stream().anyMatch(line -> line.contains(other.toAbsolutePath().normalize().toString())));
        assertEquals(
                second.toAbsolutePath().normalize(),
                RouteArtifactCache.find(
                                CACHE_DIR_PROPERTY,
                                CACHE_ENABLED_PROPERTY,
                                CACHE_FILE,
                                "model-key",
                                Files::isRegularFile)
                        .orElseThrow());
    }
}
