package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The converter is driven by a fake "python" ({@code /bin/sh}) running a stub script, so process
 * handling, caching and failure reporting are tested without llama.cpp or Python installed.
 */
class LocalSafetensorGgufConverterTest {

    @TempDir
    Path tmp;

    // ---- pure logic -----------------------------------------------------------------------

    @Test
    void outTypeDefaultsToQ8AndAcceptsScriptTypes() {
        assertEquals("q8_0", LocalSafetensorGgufConverter.normalizeOutType(null));
        assertEquals("q8_0", LocalSafetensorGgufConverter.normalizeOutType("  "));
        assertEquals("f16", LocalSafetensorGgufConverter.normalizeOutType("F16"));
        assertEquals("bf16", LocalSafetensorGgufConverter.normalizeOutType("BF16"));
        assertEquals("q8_0", LocalSafetensorGgufConverter.normalizeOutType("Q8_0"));
    }

    @Test
    void kQuantsAreRejectedInsteadOfSilentlySubstituted() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LocalSafetensorGgufConverter.normalizeOutType("Q4_K_M"));
        assertTrue(e.getMessage().contains("llama-quantize"), e.getMessage());
    }

    @Test
    void cachePathIsStableAndDistinguishesCheckpointAndType() {
        Path cache = tmp.resolve("cache");
        Path a = tmp.resolve("models/llama");
        Path b = tmp.resolve("other/llama"); // same folder name, different location
        Path p1 = LocalSafetensorGgufConverter.cachedGgufPath(a, "q8_0", cache);
        assertEquals(p1, LocalSafetensorGgufConverter.cachedGgufPath(a, "q8_0", cache));
        assertNotEquals(p1, LocalSafetensorGgufConverter.cachedGgufPath(b, "q8_0", cache));
        assertNotEquals(p1, LocalSafetensorGgufConverter.cachedGgufPath(a, "f16", cache));
        assertTrue(p1.getFileName().toString().startsWith("llama-"), p1.toString());
        assertTrue(p1.getFileName().toString().endsWith("-q8_0.gguf"), p1.toString());
    }

    @Test
    void scriptIsFoundFromExplicitPathSourceDirOrVendoredDefault() throws Exception {
        Path home = Files.createDirectory(tmp.resolve("home"));
        Path script = Files.writeString(tmp.resolve("custom.py"), "x");
        assertEquals(script, LocalSafetensorGgufConverter.findScript(
                Map.of("AQLI_GGUF_CONVERTER_SCRIPT", script.toString()), new Properties(), home));

        Path src = Files.createDirectory(tmp.resolve("llama-src"));
        Path inSrc = Files.writeString(src.resolve(LocalSafetensorGgufConverter.SCRIPT_NAME), "x");
        assertEquals(inSrc, LocalSafetensorGgufConverter.findScript(
                Map.of("AQLI_LLAMA_SOURCE_DIR", src.toString()), new Properties(), home));

        Path vendored = Files.createDirectories(home.resolve(".aqli/source/vendor/llama.cpp"));
        Path vScript = Files.writeString(vendored.resolve(LocalSafetensorGgufConverter.SCRIPT_NAME), "x");
        assertEquals(vScript, LocalSafetensorGgufConverter.findScript(Map.of(), new Properties(), home));
    }

    @Test
    void sourceDirWithoutScriptIsAMisconfigurationNotAHint() throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home/.aqli/source/vendor/llama.cpp"));
        Files.writeString(home.resolve(LocalSafetensorGgufConverter.SCRIPT_NAME), "x");
        Path emptySrc = Files.createDirectory(tmp.resolve("empty-src"));
        assertNull(LocalSafetensorGgufConverter.findScript(
                Map.of("AQLI_LLAMA_SOURCE_DIR", emptySrc.toString()), new Properties(), tmp.resolve("home")));
    }

    @Test
    void missingScriptExplainsHowToProvideIt() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> LocalSafetensorGgufConverter.resolveConfig(Map.of(), new Properties(), tmp.resolve("nohome")));
        assertTrue(e.getMessage().contains("AQLI_GGUF_CONVERTER_SCRIPT"), e.getMessage());
        assertTrue(e.getMessage().contains("AQLI_LLAMA_SOURCE_DIR"), e.getMessage());
    }

    // ---- process-driven behaviour -----------------------------------------------------------

    private Path checkpoint() throws IOException {
        Path dir = Files.createDirectory(tmp.resolve("ckpt"));
        Files.writeString(dir.resolve("config.json"), "{}");
        Files.writeString(dir.resolve("model.safetensors"), "weights");
        return dir;
    }

    /** "python" is /bin/sh, the "script" records each run and writes a fake GGUF. */
    private LocalSafetensorGgufConverter.Config fakeConfig(String body) throws IOException {
        Path script = Files.writeString(tmp.resolve("convert_hf_to_gguf.py"), body);
        return new LocalSafetensorGgufConverter.Config("/bin/sh", script, tmp.resolve("cache"));
    }

    private static final String OK_SCRIPT = String.join("\n",
            "# args: $1=dir $2=--outfile $3=file $4=--outtype $5=type",
            "echo run >> \"$(dirname \"$3\")/runs.log\"",
            "printf 'GGUF-%s' \"$5\" > \"$3\"",
            "echo converted");

    @Test
    void convertsOnceThenServesFromCache() throws Exception {
        Path ckpt = checkpoint();
        var config = fakeConfig(OK_SCRIPT);
        List<String> log = new ArrayList<>();

        var first = LocalSafetensorGgufConverter.convert(ckpt, "f16", config, log::add);
        assertFalse(first.cached());
        assertEquals("f16", first.outType());
        assertEquals("GGUF-f16", Files.readString(first.gguf()));
        assertFalse(Files.exists(first.gguf().resolveSibling(first.gguf().getFileName() + ".part")));

        var second = LocalSafetensorGgufConverter.convert(ckpt, "f16", config, log::add);
        assertTrue(second.cached());
        assertEquals(first.gguf(), second.gguf());
        List<String> runs = Files.readAllLines(config.cacheDir().resolve("runs.log"));
        assertEquals(1, runs.size(), "converter must run only once");
    }

    @Test
    void changedCheckpointInvalidatesTheCache() throws Exception {
        Path ckpt = checkpoint();
        var config = fakeConfig(OK_SCRIPT);
        var first = LocalSafetensorGgufConverter.convert(ckpt, "bf16", config, s -> { });
        Files.setLastModifiedTime(ckpt.resolve("model.safetensors"),
                FileTime.fromMillis(System.currentTimeMillis() + 3_600_000L));
        var again = LocalSafetensorGgufConverter.convert(ckpt, "bf16", config, s -> { });
        assertFalse(again.cached());
        assertEquals(first.gguf(), again.gguf());
        assertEquals(2, Files.readAllLines(config.cacheDir().resolve("runs.log")).size());
    }

    @Test
    void failureIsReportedWithDependencyHintAndLeavesNoPartialFile() throws Exception {
        Path ckpt = checkpoint();
        var config = fakeConfig("echo \"ModuleNotFoundError: No module named 'torch'\"\nexit 1\n");
        IOException e = assertThrows(IOException.class,
                () -> LocalSafetensorGgufConverter.convert(ckpt, "q8_0", config, s -> { }));
        assertTrue(e.getMessage().contains("exit 1"), e.getMessage());
        assertTrue(e.getMessage().contains("pip install"), e.getMessage());
        assertTrue(e.getMessage().contains("No module named 'torch'"), e.getMessage());
        try (var files = Files.list(config.cacheDir())) {
            assertEquals(0, files.count(), "no .part or .gguf may be left behind");
        }
    }

    @Test
    void successfulExitWithoutOutputIsAnError() throws Exception {
        Path ckpt = checkpoint();
        var config = fakeConfig("exit 0\n");
        IOException e = assertThrows(IOException.class,
                () -> LocalSafetensorGgufConverter.convert(ckpt, "q8_0", config, s -> { }));
        assertTrue(e.getMessage().contains("no GGUF"), e.getMessage());
    }
}
