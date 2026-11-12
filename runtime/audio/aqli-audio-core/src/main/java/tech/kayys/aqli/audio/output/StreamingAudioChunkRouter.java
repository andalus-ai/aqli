package tech.kayys.aqli.audio.output;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Routes encoded streaming audio chunks to live PCM preview or an encoded output session.
 *
 * <p>Audio payload decoding and metadata-based stream classification are reusable audio
 * concerns. The caller retains ownership of output-path policy and supplies the resolver
 * only when encoded chunks need direct-to-file publication.</p>
 */
public final class StreamingAudioChunkRouter {
    private final StreamingAudioOutputSession encodedOutput;
    private final LivePcmAudioPlayer livePcmPlayer;
    private final Function<Map<String, Object>, Path> outputPathResolver;

    public StreamingAudioChunkRouter(
            StreamingAudioOutputSession encodedOutput,
            LivePcmAudioPlayer livePcmPlayer,
            Function<Map<String, Object>, Path> outputPathResolver) {
        this.encodedOutput = Objects.requireNonNull(encodedOutput, "encodedOutput");
        this.livePcmPlayer = Objects.requireNonNull(livePcmPlayer, "livePcmPlayer");
        this.outputPathResolver = Objects.requireNonNull(outputPathResolver, "outputPathResolver");
    }

    /**
     * Accepts a Base64-encoded audio delta. Malformed or empty chunks are ignored,
     * preserving the best-effort behavior expected by streaming consumers.
     */
    public void accept(String base64Delta, Map<String, Object> metadata) {
        if (base64Delta == null || base64Delta.isBlank()) {
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Delta);
            if (isPcmStreamChunk(metadata)) {
                livePcmPlayer.write(decoded, metadata);
                return;
            }
            boolean streamToFile = shouldStreamEncodedAudioToFile(metadata);
            Path targetPath = streamToFile ? outputPathResolver.apply(metadata) : null;
            encodedOutput.write(decoded, streamToFile, targetPath);
        } catch (Exception ignored) {
            // Streaming audio is best-effort; a malformed chunk must not fail inference.
        }
    }

    static boolean isPcmStreamChunk(Map<String, Object> metadata) {
        String payload = metadataText(metadata, "audio_stream_payload");
        return "pcm_s16le".equalsIgnoreCase(payload)
                || "true".equalsIgnoreCase(metadataText(metadata, "audio_stream_preview"));
    }

    static boolean shouldStreamEncodedAudioToFile(Map<String, Object> metadata) {
        String payload = metadataText(metadata, "audio_stream_payload");
        return "encoded".equalsIgnoreCase(payload)
                || metadataText(metadata, "audio_format") != null
                || metadataText(metadata, "audio_mime") != null;
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }
}
