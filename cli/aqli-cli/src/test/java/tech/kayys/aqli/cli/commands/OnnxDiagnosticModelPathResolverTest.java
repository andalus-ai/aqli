package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OnnxDiagnosticModelPathResolverTest {

    @Test
    void prefersResolvedLocalPathOverModelReference() {
        Path resolved = Path.of("/models/resolved");
        Path requested = Path.of("/models/requested");

        Path result = OnnxDiagnosticModelPathResolver.resolve(
                resolved.toString(), requested.toString(),
                value -> value == null ? null : Path.of(value));

        assertEquals(resolved, result);
    }

    @Test
    void fallsBackToModelReferenceWhenResolvedPathIsUnavailable() {
        Path requested = Path.of("/models/requested");

        Path result = OnnxDiagnosticModelPathResolver.resolve(
                null, requested.toString(),
                value -> value == null ? null : Path.of(value));

        assertEquals(requested, result);
    }

    @Test
    void returnsNullWhenNeitherCandidateResolves() {
        assertNull(OnnxDiagnosticModelPathResolver.resolve(null, null, value -> null));
    }
}
