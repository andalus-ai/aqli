/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.plugin.core.ExtensionAvailability;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Availability provider for the optional Suling audio extension.
 */
public final class SulingAudioExtensionAvailabilityProvider implements ExtensionAvailabilityProvider {
    public static final String ID = "suling";

    @Override
    public String extensionId() {
        return ID;
    }

    @Override
    public String extensionName() {
        return "Suling Audio";
    }

    @Override
    public String extensionKind() {
        return "audio";
    }

    @Override
    public ExtensionAvailability availability() {
        OptionalSulingAudio.BackendDiagnostics diagnostics = OptionalSulingAudio.backendDiagnostics();
        boolean attached = diagnostics.attached();
        boolean flacAvailable = OptionalSulingAudio.flacAvailable();
        boolean mp3Available = OptionalSulingAudio.mp3EncodingAvailable();
        boolean fallback = attached && isSulingFallbackDiagnostics(diagnostics.diagnostics());
        String status = !attached
                ? "detached"
                : fallback
                        ? "fallback"
                        : "ready";
        List<String> hints = new ArrayList<>(diagnostics.remediationHints());
        if (fallback) {
            hints.add("Install or attach the external Suling audio module for FLAC/MP3 encoders; "
                    + "the packaged fallback is WAV-only.");
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("flacAvailable", Boolean.toString(flacAvailable));
        attributes.put("flacVersion", OptionalSulingAudio.flacVersion());
        attributes.put("mp3EncodingAvailable", Boolean.toString(mp3Available));
        attributes.put("fallback", Boolean.toString(fallback));
        return new ExtensionAvailability(
                extensionId(),
                extensionName(),
                extensionKind(),
                attached,
                !attached,
                true,
                attached && !fallback,
                status,
                List.of("audio_encoding", "tts_audio_output"),
                diagnostics.formats(),
                attributes,
                diagnostics.diagnostics(),
                hints);
    }

    private static boolean isSulingFallbackDiagnostics(String diagnostics) {
        if (diagnostics == null || diagnostics.isBlank()) {
            return false;
        }
        return diagnostics.toLowerCase(java.util.Locale.ROOT).contains("fallback");
    }
}
