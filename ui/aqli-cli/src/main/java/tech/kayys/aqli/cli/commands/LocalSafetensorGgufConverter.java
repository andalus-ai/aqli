/*
 * Aqli CLI
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.cli.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Converts a local HuggingFace safetensors checkpoint to a GGUF file so the llama.cpp backend
 * ({@code --llamacpp}) can run it. llama.cpp cannot read safetensors itself.
 *
 * <p>Uses llama.cpp's own {@code convert_hf_to_gguf.py}, located with the same conventions as the
 * GGUF runner's {@code ModelConverterService}: {@code AQLI_GGUF_CONVERTER_SCRIPT},
 * {@code AQLI_LLAMA_SOURCE_DIR}, then {@code ~/.aqli/source/vendor/llama.cpp}. The result is cached
 * under {@code ~/.aqli/cache/gguf-from-safetensors} and reused until the checkpoint changes, so the
 * conversion cost is paid once.
 *
 * <p>The script can emit {@code f32}, {@code f16}, {@code bf16} and {@code q8_0}. Smaller K-quants
 * (Q4_K_M, ...) need {@code llama-quantize} and are rejected here with a clear message rather than
 * silently substituted.
 *
 * <p>JDK-only on purpose, so it is usable and testable without the CLI's container.
 */
final class LocalSafetensorGgufConverter {

    static final String SCRIPT_NAME = "convert_hf_to_gguf.py";

    /** Out types {@code convert_hf_to_gguf.py --outtype} accepts directly. */
    static final Set<String> SCRIPT_OUT_TYPES = Set.of("f32", "f16", "bf16", "q8_0");

    /** Default: half the size of f16 and near-lossless, which matters for large checkpoints. */
    static final String DEFAULT_OUT_TYPE = "q8_0";

    record Config(String pythonCommand, Path script, Path cacheDir) {
    }

    record Result(Path gguf, boolean cached, String outType) {
    }

    private LocalSafetensorGgufConverter() {
    }

    // ------------------------------------------------------------------ configuration

    /**
     * Resolves the converter script and cache location from the environment. Throws with
     * actionable guidance when no script can be found.
     */
    static Config resolveConfig(Map<String, String> env, Properties props, Path home) {
        String python = firstNonBlank(env.get("AQLI_GGUF_PYTHON"), props.getProperty("aqli.gguf.python.command"),
                "python3");
        Path script = findScript(env, props, home);
        if (script == null) {
            throw new IllegalStateException("llama.cpp's " + SCRIPT_NAME + " was not found. Point to it with "
                    + "AQLI_GGUF_CONVERTER_SCRIPT=/path/to/" + SCRIPT_NAME + " or set AQLI_LLAMA_SOURCE_DIR to a "
                    + "llama.cpp checkout (default location: " + home.resolve(".aqli/source/vendor/llama.cpp") + ")");
        }
        String cache = firstNonBlank(env.get("AQLI_GGUF_CACHE_DIR"), props.getProperty("aqli.gguf.cache.dir"), null);
        Path cacheDir = cache != null ? Path.of(cache) : home.resolve(".aqli/cache/gguf-from-safetensors");
        return new Config(python, script, cacheDir);
    }

    static Path findScript(Map<String, String> env, Properties props, Path home) {
        String explicit = firstNonBlank(env.get("AQLI_GGUF_CONVERTER_SCRIPT"),
                props.getProperty("aqli.gguf.converter.script"), null);
        if (explicit != null && Files.isRegularFile(Path.of(explicit.trim()))) {
            return Path.of(explicit.trim());
        }
        String sourceDir = env.get("AQLI_LLAMA_SOURCE_DIR");
        if (sourceDir != null && !sourceDir.isBlank()) {
            Path candidate = Path.of(sourceDir.trim()).resolve(SCRIPT_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            return null; // an explicit source dir without the script is a misconfiguration, not a hint
        }
        Path vendored = home.resolve(".aqli/source/vendor/llama.cpp").resolve(SCRIPT_NAME);
        return Files.isRegularFile(vendored) ? vendored : null;
    }

    /** Maps a user-requested type ({@code --gguf-quant} / {@code --gguf-out-type}) to a script out type. */
    static String normalizeOutType(String requested) {
        if (requested == null || requested.isBlank()) {
            return DEFAULT_OUT_TYPE;
        }
        String t = requested.trim().toLowerCase(Locale.ROOT);
        if (SCRIPT_OUT_TYPES.contains(t)) {
            return t;
        }
        throw new IllegalArgumentException("'" + requested + "' cannot be produced by the converter script, which "
                + "supports only " + String.join(", ", new java.util.TreeSet<>(SCRIPT_OUT_TYPES)) + ". Convert to "
                + "one of those, then quantize the GGUF with llama.cpp's llama-quantize");
    }

    // ------------------------------------------------------------------ cache

    static Path cachedGgufPath(Path checkpointDir, String outType, Path cacheDir) {
        Path abs = checkpointDir.toAbsolutePath().normalize();
        String name = abs.getFileName() == null ? "model" : abs.getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cacheDir.resolve(name + "-" + shortHash(abs.toString()) + "-" + outType + ".gguf");
    }

    /** A cached GGUF is reusable if it exists, is non-empty, and is newer than the checkpoint's files. */
    static boolean isFresh(Path gguf, Path checkpointDir) throws IOException {
        if (!Files.isRegularFile(gguf) || Files.size(gguf) == 0) {
            return false;
        }
        long cached = Files.getLastModifiedTime(gguf).toMillis();
        try (Stream<Path> files = Files.list(checkpointDir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String n = f.getFileName().toString();
                if ((n.endsWith(".safetensors") || n.endsWith(".json") || n.endsWith(".model"))
                        && Files.isRegularFile(f) && Files.getLastModifiedTime(f).toMillis() > cached) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ conversion

    /**
     * Returns a GGUF for the checkpoint, converting it first when no fresh cached copy exists.
     *
     * @param log receives human-readable progress lines (converter output included)
     */
    static Result convert(Path checkpointDir, String requestedType, Config config, Consumer<String> log)
            throws IOException, InterruptedException {
        String outType = normalizeOutType(requestedType);
        Path target = cachedGgufPath(checkpointDir, outType, config.cacheDir());
        if (isFresh(target, checkpointDir)) {
            log.accept("Using cached GGUF (" + outType + "): " + target);
            return new Result(target, true, outType);
        }

        Files.createDirectories(config.cacheDir());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(part);
        log.accept("Converting safetensors checkpoint to GGUF (" + outType + ") for llama.cpp: " + checkpointDir);
        log.accept("This runs once; the result is cached at " + target);

        List<String> command = new ArrayList<>();
        command.add(config.pythonCommand());
        command.add(config.script().toAbsolutePath().toString());
        command.add(checkpointDir.toAbsolutePath().toString());
        command.add("--outfile");
        command.add(part.toAbsolutePath().toString());
        command.add("--outtype");
        command.add(outType);

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                tail.addLast(line);
                if (tail.size() > 40) {
                    tail.removeFirst();
                }
            }
        }
        int exit = process.waitFor();
        if (exit != 0) {
            Files.deleteIfExists(part);
            throw new IOException(failureMessage(exit, String.join("\n", tail), config));
        }
        if (!Files.isRegularFile(part) || Files.size(part) == 0) {
            Files.deleteIfExists(part);
            throw new IOException("The converter exited successfully but produced no GGUF file");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.accept("Conversion finished: " + target);
        return new Result(target, false, outType);
    }

    static String failureMessage(int exit, String output, Config config) {
        StringBuilder sb = new StringBuilder("GGUF conversion failed (exit " + exit + ")");
        if (output.contains("No module named")) {
            sb.append(": the converter's Python dependencies are missing. Install them with: ")
                    .append(config.pythonCommand()).append(" -m pip install -r ")
                    .append(config.script().toAbsolutePath().getParent()
                            .resolve("requirements/requirements-convert_hf_to_gguf.txt"));
        }
        if (!output.isBlank()) {
            sb.append("\n").append(output);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", d[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String firstNonBlank(String a, String b, String dflt) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return dflt;
    }
}
