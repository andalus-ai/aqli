package tech.kayys.aqli.inference.gguf;

/**
 * Immutable native llama.cpp session parameters shared by one-shot and cached
 * GGUF session acquisition.
 */
public record LlamaCppGgufSessionParameters(
        int context,
        int batch,
        int microBatch,
        int threads,
        int batchThreads,
        int gpuLayers,
        boolean useMmap,
        boolean useMlock,
        boolean swaFull) {
}
