package tech.kayys.aqli.cli.commands.tts;

/** Immutable snapshot of TTS voice-catalog discovery and filtering options. */
public record TtsVoiceDiscoveryRunOptions(
        boolean listVoices,
        boolean listVoicesJson,
        String voiceSearch) {
}
