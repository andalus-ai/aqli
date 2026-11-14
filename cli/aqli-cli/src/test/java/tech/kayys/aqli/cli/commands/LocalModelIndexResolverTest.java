package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalModelIndexResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void searchesUnprefixedReferenceBeforeHuggingFacePrefixedReference() throws Exception {
        Path artifact = Files.createFile(tempDir.resolve("model.gguf"));
        LocalModelIndex.Entry entry = entry(artifact);
        List<String> references = new ArrayList<>();

        Optional<LocalModelIndex.Entry> result = LocalModelIndexResolver.find(
                "org/model",
                "gguf",
                (reference, format) -> {
                    references.add(reference + ":" + format);
                    return reference.startsWith("hf:") ? Optional.of(entry) : Optional.empty();
                });

        assertTrue(result.isPresent());
        assertSame(entry, result.orElseThrow());
        assertEquals(List.of("org/model:gguf", "hf:org/model:gguf"), references);
    }

    @Test
    void skipsEntriesWithMissingOrInvalidArtifactPaths() throws Exception {
        LocalModelIndex.Entry missing = entry(tempDir.resolve("missing.gguf"));
        LocalModelIndex.Entry valid = entry(Files.createFile(tempDir.resolve("valid.gguf")));
        List<String> references = new ArrayList<>();

        Optional<LocalModelIndex.Entry> result = LocalModelIndexResolver.find(
                "org/model",
                null,
                (reference, format) -> {
                    references.add(reference);
                    return reference.startsWith("hf:") ? Optional.of(valid) : Optional.of(missing);
                });

        assertTrue(result.isPresent());
        assertSame(valid, result.orElseThrow());
        assertEquals(List.of("org/model", "hf:org/model"), references);
    }

    @Test
    void returnsEmptyForInvalidInputAndToleratesLookupFailures() {
        assertTrue(LocalModelIndexResolver.find(null, null, (ref, format) -> Optional.empty()).isEmpty());
        assertTrue(LocalModelIndexResolver.find(" ", null, (ref, format) -> Optional.empty()).isEmpty());
        assertTrue(LocalModelIndexResolver.find("org/model", null,
                (ref, format) -> { throw new IllegalStateException("index unavailable"); }).isEmpty());
    }

    @Test
    void exactReferenceLookupDoesNotExpandAliasesOrRequireExistingArtifact() {
        LocalModelIndex.Entry stale = entry(tempDir.resolve("missing.gguf"));
        List<String> lookups = new ArrayList<>();

        Optional<LocalModelIndex.Entry> result = LocalModelIndexResolver.findExactReference(
                "hf:org/model",
                "gguf",
                (reference, format) -> {
                    lookups.add(reference + ":" + format);
                    return Optional.of(stale);
                });

        assertTrue(result.isPresent());
        assertSame(stale, result.orElseThrow());
        assertEquals(List.of("hf:org/model:gguf"), lookups);
    }

    @Test
    void exactFormatLookupPreservesPreferredThenUnfilteredFallbackOrder() throws Exception {
        Path artifact = Files.createFile(tempDir.resolve("model.safetensors"));
        LocalModelIndex.Entry wrongFormat = entry(artifact);
        wrongFormat.format = "gguf";
        LocalModelIndex.Entry safetensor = entry(artifact);
        safetensor.format = "safetensor";
        List<String> lookups = new ArrayList<>();

        Optional<LocalModelIndex.Entry> result = LocalModelIndexResolver.findExactFormat(
                "org/model",
                "safetensor",
                (reference, format) -> {
                    lookups.add(reference + ":" + format);
                    return format == null ? Optional.of(safetensor) : Optional.of(wrongFormat);
                });

        assertTrue(result.isPresent());
        assertSame(safetensor, result.orElseThrow());
        assertEquals(List.of("org/model:safetensor", "org/model:null"), lookups);
    }

    @Test
    void exactFormatLookupRejectsUnfilteredEntryOfAnotherFormat() throws Exception {
        Path artifact = Files.createFile(tempDir.resolve("other.gguf"));
        LocalModelIndex.Entry wrongFormat = entry(artifact);
        wrongFormat.format = "gguf";

        Optional<LocalModelIndex.Entry> result = LocalModelIndexResolver.findExactFormat(
                "org/model",
                "safetensor",
                (reference, format) -> Optional.of(wrongFormat));

        assertTrue(result.isEmpty());
    }

    private static LocalModelIndex.Entry entry(Path path) {
        LocalModelIndex.Entry entry = new LocalModelIndex.Entry();
        entry.path = path.toString();
        return entry;
    }
}
