package tech.kayys.aqli.cli.commands;

import java.nio.charset.StandardCharsets;

/**
 * Immutable llama.cpp session settings resolved for one GGUF run or prewarm.
 *
 * <p>CLI/runtime selection remains in {@link GgufStandaloneRunner}; this value
 * only captures the concrete parameters used to open and reuse a native session.</p>
 */
record GgufLlamaCppRunOptions(
        int context,
        int batch,
        int microBatch,
        int threads,
        int batchThreads,
        int gpuLayers,
        boolean swaFull,
        boolean useMmap,
        boolean useMlock) {

    static GgufLlamaCppRunOptions resolve(
            String backend,
            String prompt,
            int maxTokens,
            boolean prewarm,
            int defaultThreads,
            GgufStandaloneConfiguration configuration) {
        int threads = Math.max(1, configuration.intValue("aqli.gguf.fast_run.threads", defaultThreads));
        int batchThreads = Math.max(1, configuration.intValue("aqli.gguf.fast_run.batch_threads", threads));
        int context = prewarm
                ? prewarmContext(prompt, maxTokens, configuration)
                : context(prompt, maxTokens, configuration);
        int batch = boundedBatch(
                "aqli.gguf.fast_run.batch", Math.min(context, 1024), context, configuration);
        int microBatch = boundedBatch(
                "aqli.gguf.fast_run.ubatch", Math.min(batch, 512), batch, configuration);
        int gpuLayers = "cpu".equals(backend)
                ? 0
                : configuration.intValue("aqli.gguf.fast_run.gpu_layers", -1);

        return new GgufLlamaCppRunOptions(
                context,
                batch,
                microBatch,
                threads,
                batchThreads,
                gpuLayers,
                configuration.booleanValue("aqli.gguf.fast_run.swa_full", false),
                configuration.booleanValue("aqli.gguf.fast_run.mmap", true),
                configuration.booleanValue("aqli.gguf.fast_run.mlock", false));
    }

    static int context(String prompt, int maxTokens, GgufStandaloneConfiguration configuration) {
        String configured = configuration.value("aqli.gguf.fast_run.context");
        if (isExplicitContext(configured)) {
            return GgufStandaloneConfiguration.parsePositiveInt(configured, 2048);
        }

        int maximum = maxAutoContext(configuration);
        int promptBytes = prompt == null ? 0 : prompt.getBytes(StandardCharsets.UTF_8).length;
        int requested = (int) Math.min(
                Integer.MAX_VALUE, (long) promptBytes + Math.max(1, maxTokens) + 128L);
        return nextPowerOfTwoBounded(requested, 512, maximum);
    }

    static int prewarmContext(String prompt, int maxTokens, GgufStandaloneConfiguration configuration) {
        String configured = configuration.value("aqli.gguf.fast_run.context");
        if (isExplicitContext(configured)) {
            return GgufStandaloneConfiguration.parsePositiveInt(configured, 2048);
        }

        int maximum = maxAutoContext(configuration);
        int minimum = prewarmMinimumContext(maximum, configuration);
        int requested = Math.max(context(prompt, maxTokens, configuration), minimum);
        return nextPowerOfTwoBounded(requested, 512, maximum);
    }

    static int boundedBatch(
            String property,
            int defaultValue,
            int maxValue,
            GgufStandaloneConfiguration configuration) {
        int requested = configuration.intValue(property, defaultValue);
        return Math.max(1, Math.min(requested, Math.max(1, maxValue)));
    }

    private static int prewarmMinimumContext(
            int maximum,
            GgufStandaloneConfiguration configuration) {
        String configured = configuration.value("aqli.gguf.fast_run.prewarm_min_context");
        int requested = GgufStandaloneConfiguration.parsePositiveInt(
                configured == null || configured.isBlank() ? Integer.toString(maximum) : configured,
                maximum);
        return Math.min(maximum, requested);
    }

    private static int maxAutoContext(GgufStandaloneConfiguration configuration) {
        return configuration.positiveInt("aqli.gguf.fast_run.max_auto_context", 4096);
    }

    private static boolean isExplicitContext(String configured) {
        return configured != null && !configured.isBlank() && !configured.trim().equalsIgnoreCase("auto");
    }

    private static int nextPowerOfTwoBounded(int value, int minimum, int maximum) {
        int lowerBound = Math.max(1, minimum);
        int upperBound = Math.max(lowerBound, maximum);
        int context = lowerBound;
        int target = Math.max(lowerBound, value);
        while (context < target && context < upperBound) {
            context = Math.min(context << 1, upperBound);
        }
        return context;
    }
}
