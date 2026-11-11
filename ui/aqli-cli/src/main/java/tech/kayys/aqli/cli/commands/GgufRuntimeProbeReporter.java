package tech.kayys.aqli.cli.commands;

import java.io.PrintStream;

import tech.kayys.aljabr.gguf.runtime.GgufRuntimeProfile;
import tech.kayys.aljabr.gguf.runtime.GgufRuntimeProbe;

/** Formats GGUF runtime-profile and tensor-probe diagnostics for the CLI. */
final class GgufRuntimeProbeReporter {
    private GgufRuntimeProbeReporter() {
    }

    static void printProfile(PrintStream out, String label, GgufRuntimeProfile profile) {
        out.printf(
                "%s: architecture=%s, ggufVersion=%d, tensors=%d, metadata=%d, size=%.2fGiB, "
                        + "load=%.2fs, knownTypes=%.1f%%, decoderTensors=%d/%d, types=%s, status=%s.%n",
                label,
                profile.architecture(),
                profile.ggufVersion(),
                profile.tensorCount(),
                profile.metadataCount(),
                profile.modelBytes() / 1024.0d / 1024.0d / 1024.0d,
                profile.loadMillis() / 1000.0d,
                profile.knownTensorTypeRatio() * 100.0d,
                profile.presentDecoderTensorCount(),
                profile.requiredDecoderTensorCount(),
                profile.compactTypeSummary(4),
                profile.javaStatus());
        if (!profile.missingDecoderTensorExamples().isEmpty()) {
            out.printf(
                    "%s missing decoder tensors: count=%d, examples=%s.%n",
                    label,
                    profile.missingDecoderTensorCount(),
                    String.join(", ", profile.missingDecoderTensorExamples()));
        }
        if (!profile.malformedDecoderTensorExamples().isEmpty()) {
            out.printf(
                    "%s invalid decoder tensor shapes: count=%d, examples=%s.%n",
                    label,
                    profile.malformedDecoderTensorCount(),
                    String.join(", ", profile.malformedDecoderTensorExamples()));
        }
        out.printf("%s readiness: %s.%n", label, javaReadinessSummary(profile));
        if (profile.modelConfig() != null) {
            out.printf(
                    "%s config: type=%s, layers=%d, hidden=%d, heads=%d/%d, headDim=%d, context=%d, vocab=%d.%n",
                    label,
                    profile.modelConfig().modelType(),
                    profile.modelConfig().numHiddenLayers(),
                    profile.modelConfig().hiddenSize(),
                    profile.modelConfig().numAttentionHeads(),
                    profile.modelConfig().resolvedNumKvHeads(),
                    profile.modelConfig().resolvedHeadDim(),
                    profile.modelConfig().maxPositionEmbeddings(),
                    profile.modelConfig().vocabSize());
        }
    }

    static String javaReadinessSummary(GgufRuntimeProfile profile) {
        boolean loaderReady = profile != null && profile.knownTensorTypeRatio() > 0.0d;
        boolean decoderTensorsReady = profile != null && profile.decoderTensorSetComplete();
        boolean rowDotReady = profile != null && profile.rowDotPrimitivesReady();
        return "loaderReady=" + loaderReady
                + ", decoderTensorsReady=" + decoderTensorsReady
                + ", rowDotReady=" + rowDotReady
                + ", generationReady=false";
    }

    static void printRowDotProbe(PrintStream out, String label, GgufRuntimeProbe probe) {
        if (!probe.hasTensorProbe()) {
            out.printf("%s row-dot probe: unavailable, no supported matrix tensor.%n", label);
            return;
        }
        out.printf("%s tensor probe: %s.%n", label, probe.compactSummary());
        if (probe.hasPreparedMatrixCachePlan()) {
            out.printf(
                    "%s decoder prepared-cache decision: %s.%n",
                    label,
                    probe.preparedMatrixCacheDecision().selectionSummary());
            out.printf(
                    "%s decoder prepared-cache plan: %s.%n",
                    label,
                    probe.preparedMatrixCachePlan().compactSummary());
        }
        if (probe.hasPreparedMatrixCacheProbe()) {
            out.printf(
                    "%s decoder prepared-cache probe: %s.%n",
                    label,
                    probe.preparedMatrixCacheStats().compactSummary());
        }
    }
}
