package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalModelIndexReferenceMatcherTest {

    @Test
    void exactIdentifierMatchesAreCaseInsensitive() {
        LocalModelIndex.Entry entry = entry("org/model", "model", "gguf", "/models/org/model.gguf");

        assertTrue(LocalModelIndexReferenceMatcher.findBestMatch(
                List.of(entry), "ORG/MODEL").isPresent());
    }

    @Test
    void formatAliasesAreCanonicalizedBeforeComparison() {
        LocalModelIndex.Entry entry = entry("org/model", "model", "safetensor", "/models/model.safetensors");

        assertTrue(LocalModelIndexReferenceMatcher.formatMatches(entry, "safetensors"));
        assertTrue(LocalModelIndexReferenceMatcher.formatMatches(entry("x", "x", "tflite", "/x"), "litert"));
        assertTrue(LocalModelIndexReferenceMatcher.formatMatches(entry("x", "x", "pt", "/x"), "torchscript"));
    }

    @Test
    void lookupPreservesExactBeforePathSuffixPrecedence() {
        LocalModelIndex.Entry exact = entry("org/repository/model.gguf", "model.gguf", "gguf", "/cache/other/model.gguf");
        LocalModelIndex.Entry suffix = entry("different", "model.gguf", "gguf", "/cache/org/repository/model.gguf");

        assertEquals(exact, LocalModelIndexReferenceMatcher.findBestMatch(
                List.of(exact, suffix), "org/repository/model.gguf").orElseThrow());
    }

    private static LocalModelIndex.Entry entry(String id, String name, String format, String path) {
        LocalModelIndex.Entry entry = new LocalModelIndex.Entry();
        entry.id = id;
        entry.name = name;
        entry.format = format;
        entry.path = path;
        return entry;
    }
}
