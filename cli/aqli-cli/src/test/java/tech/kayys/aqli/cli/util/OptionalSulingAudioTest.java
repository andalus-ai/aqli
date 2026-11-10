package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptionalSulingAudioTest {
    @Test
    void diagnosticsAreSafeWhenSulingIsAttachedOrDetached() {
        OptionalSulingAudio.BackendDiagnostics diagnostics = OptionalSulingAudio.backendDiagnostics();

        assertNotNull(diagnostics.formats());
        assertNotNull(diagnostics.remediationHints());
        assertNotNull(diagnostics.diagnostics());
        assertFalse(diagnostics.diagnostics().isBlank());
        assertNotNull(OptionalSulingAudio.flacVersion());
    }
}
