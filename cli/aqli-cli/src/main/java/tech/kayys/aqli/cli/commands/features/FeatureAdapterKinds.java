package tech.kayys.aqli.cli.commands.features;

/**
 * Canonical identifiers for extension adapter kinds exposed by AQLI.
 *
 * <p>This CLI-level vocabulary remains separate from the SDK capability
 * registry. It is used for extension scaffold metadata and legacy command
 * compatibility.</p>
 */
public final class FeatureAdapterKinds {

    public static final String PIPELINE = "pipeline";
    public static final String RUNNER = "runner";
    public static final String TRAINING = "training";
    public static final String BACKEND = "backend";
    public static final String CONVERTER = "converter";
    public static final String EXPORTER = "exporter";
    public static final String DATASET = "dataset";
    public static final String EVALUATOR = "evaluator";
    public static final String TOOLING = "tooling";
    public static final String ADAPTER = "adapter";
    public static final String OPTIMIZATION = "optimization";
    public static final String QUANTIZATION = "quantization";
    public static final String CAPABILITY = "capability";

    private FeatureAdapterKinds() {
    }

    public static String normalize(String kind) {
        return kind;
    }
}
