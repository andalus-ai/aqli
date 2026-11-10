package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reflection-backed bridge for the optional Suling audio extension.
 */
public final class OptionalSulingAudio {
    private static final String SULING_CLASS = "tech.kayys.suling.audio.Suling";
    private static final String FLAC_CLASS = "tech.kayys.suling.FlacLibraryCheck";
    private static final String FFMPEG_CLASS = "tech.kayys.suling.audio.FfmpegAudioEncoder";
    private static final String DETACHED_DIAGNOSTICS = "Suling audio extension is not attached. "
            + "Attach :suling or the Suling plugin to enable MOSS TTS audio diagnostics and encoders.";

    private OptionalSulingAudio() {
    }

    public record BackendDiagnostics(
            boolean attached,
            List<String> formats,
            String diagnostics,
            List<String> remediationHints) {
        public BackendDiagnostics {
            formats = List.copyOf(formats == null ? List.of() : formats);
            diagnostics = diagnostics == null || diagnostics.isBlank() ? DETACHED_DIAGNOSTICS : diagnostics;
            remediationHints = List.copyOf(remediationHints == null ? List.of() : remediationHints);
        }

        public boolean requiresAttention() {
            return !attached;
        }
    }

    public static BackendDiagnostics backendDiagnostics() {
        boolean attached = sulingAttached();
        List<String> formats = supportedAudioFormats();
        String diagnostics = diagnosticsText();
        List<String> hints = attached
                ? List.of()
                : List.of("Attach :suling or install the Suling audio plugin to enable audio generation backends.");
        return new BackendDiagnostics(attached, formats, diagnostics, hints);
    }

    public static boolean sulingAttached() {
        return classAvailable(SULING_CLASS);
    }

    public static List<String> supportedAudioFormats() {
        Object value = invokeStatic(SULING_CLASS, "supportedAudioFormats");
        if (value instanceof Collection<?> values) {
            List<String> formats = new ArrayList<>();
            for (Object item : values) {
                if (item != null && !item.toString().isBlank()) {
                    formats.add(item.toString());
                }
            }
            return List.copyOf(formats);
        }
        return List.of();
    }

    public static String diagnosticsText() {
        Object value = invokeStatic(SULING_CLASS, "diagnostics");
        if (value != null && !value.toString().isBlank()) {
            return value.toString();
        }
        return DETACHED_DIAGNOSTICS;
    }

    public static boolean flacAvailable() {
        Object value = invokeStatic(FLAC_CLASS, "isAvailable");
        return value instanceof Boolean available && available;
    }

    public static String flacVersion() {
        Object value = invokeStatic(FLAC_CLASS, "getVersion");
        return value == null || value.toString().isBlank() ? "unavailable" : value.toString();
    }

    public static boolean mp3EncodingAvailable() {
        Object value = invokeStatic(FFMPEG_CLASS, "isMp3EncodingAvailable");
        return value instanceof Boolean available && available;
    }

    private static boolean classAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static Object invokeStatic(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getMethod(methodName);
            return method.invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
