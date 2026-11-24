package tech.kayys.aqli.cli.commands.features.scaffold;

import tech.kayys.aqli.cli.commands.features.FeatureAdapterKinds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical catalog of extension scaffold kinds exposed by the AQLI CLI.
 *
 * <p>This class contains immutable scaffold metadata only. Picocli command
 * parsing and terminal/JSON rendering remain in {@code FeaturesCommand}.</p>
 */
public final class ScaffoldKindCatalog {

    private static final String MODEL_FAMILY_KIND = "model-family";

    private ScaffoldKindCatalog() {
    }

    public static List<ScaffoldKind> all() {
        return List.of(
                scaffoldKind(
                        FeatureAdapterKinds.PIPELINE,
                        "ModelPipeline",
                        List.of("text"),
                        List.of("text"),
                        List.of(),
                        "multi-step inference orchestration",
                        "aqli extensions init features/aqli-ocr-lab --kind pipeline --pipeline-id ocr-lab --input image,text --output text,document --provider onnx --format onnx"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.TRAINING,
                        List.of("llm", "vision", "audio"),
                        "training loops, LoRA, fine-tuning, experiments",
                        "aqli extensions init features/aqli-lora-lab --kind training --adapter-id lora-trainer --input dataset,model --output model,metrics --target llm"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.OPTIMIZATION,
                        List.of("llm", "edge"),
                        "graph rewrites, pruning, tuning, compile passes",
                        "aqli extensions init features/aqli-opt-lab --kind optimization --adapter-id graph-optimizer --input model --output model --target edge"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.QUANTIZATION,
                        List.of("llm"),
                        "AWQ, GPTQ, INT8, FP8, or format-aware quantizers",
                        "aqli extensions init features/aqli-awq-lab --kind quantization --adapter-id awq-quantizer --input model --output model --target llm --format safetensors,gguf"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.BACKEND,
                        List.of("cpu", "metal", "cuda", "rocm"),
                        "backend bridges, kernels, delegates, device runtimes",
                        "aqli extensions init features/aqli-metal-lab --kind backend --adapter-id metal-backend --input tensor --output tensor --target metal"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.EXPORTER,
                        List.of("onnx", "gguf", "safetensors"),
                        "model or artifact export workflows",
                        "aqli extensions init features/aqli-export-lab --kind exporter --adapter-id onnx-exporter --input model --output artifact --format onnx"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.CONVERTER,
                        List.of("onnx", "gguf", "safetensors"),
                        "model format conversion or migration tools",
                        "aqli extensions init features/aqli-convert-lab --kind converter --adapter-id model-converter --input model --output artifact"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.DATASET,
                        List.of("llm", "vision", "audio"),
                        "dataset loaders, preprocessors, corpus tools",
                        "aqli extensions init features/aqli-data-lab --kind dataset --adapter-id corpus-loader --input source --output dataset --target llm"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.EVALUATOR,
                        List.of("llm", "vision", "audio"),
                        "benchmarks, eval harnesses, scoring tools",
                        "aqli extensions init features/aqli-eval-lab --kind evaluator --adapter-id llm-evaluator --input model,dataset --output metrics --target llm"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.RUNNER,
                        List.of("llm", "vision", "audio"),
                        "runner adapters outside the core provider path",
                        "aqli extensions init features/aqli-runner-lab --kind runner --adapter-id custom-runner --input model,request --output response"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.TOOLING,
                        List.of("research", "ops"),
                        "research, diagnostics, packaging, or ops tooling",
                        "aqli extensions init features/aqli-tools-lab --kind tooling --adapter-id report-tool --input model --output artifact"),
                adapterScaffoldKind(
                        FeatureAdapterKinds.ADAPTER,
                        List.of("custom"),
                        "generic adapter when no common kind fits yet",
                        "aqli extensions init features/aqli-custom-lab --kind adapter --adapter-id custom-adapter --input model --output artifact"),
                scaffoldKind(
                        MODEL_FAMILY_KIND,
                        "ModelFamilyPlugin",
                        List.of("config.json", "tokenizer.json"),
                        List.of("ModelFamilyDescriptor", "ModelTokenizerDescriptor"),
                        List.of("llm", "production", "optional"),
                        "detachable model-family metadata, tokenizer descriptors, and optional architecture adapters",
                        "aqli extensions init features/aqli-model-acme --kind model-family --family-id acme --model-type acme --architecture AcmeForCausalLM --capability causal_lm,tokenizer --tokenizer-metadata-status pending --tokenizer-pending-reason scaffold"));
    }

    public static List<String> defaultAdapterInputs(String kind) {
        return switch (FeatureAdapterKinds.normalize(kind)) {
            case FeatureAdapterKinds.TRAINING -> List.of("dataset", "model");
            case FeatureAdapterKinds.BACKEND -> List.of("tensor");
            case FeatureAdapterKinds.DATASET -> List.of("source");
            case FeatureAdapterKinds.EVALUATOR -> List.of("model", "dataset");
            case FeatureAdapterKinds.RUNNER -> List.of("model", "request");
            default -> List.of("model");
        };
    }

    public static List<String> defaultAdapterOutputs(String kind) {
        return switch (FeatureAdapterKinds.normalize(kind)) {
            case FeatureAdapterKinds.TRAINING -> List.of("model", "metrics");
            case FeatureAdapterKinds.BACKEND -> List.of("tensor");
            case FeatureAdapterKinds.CONVERTER, FeatureAdapterKinds.EXPORTER -> List.of("artifact");
            case FeatureAdapterKinds.DATASET -> List.of("dataset");
            case FeatureAdapterKinds.EVALUATOR -> List.of("metrics");
            case FeatureAdapterKinds.RUNNER -> List.of("response");
            case FeatureAdapterKinds.TOOLING, FeatureAdapterKinds.ADAPTER -> List.of("artifact");
            default -> List.of("model");
        };
    }

    private static ScaffoldKind adapterScaffoldKind(
            String kind,
            List<String> defaultTargets,
            String summary,
            String exampleCommand) {
        return scaffoldKind(
                FeatureAdapterKinds.normalize(kind),
                "FeatureAdapter",
                defaultAdapterInputs(kind),
                defaultAdapterOutputs(kind),
                defaultTargets,
                summary,
                exampleCommand);
    }

    private static ScaffoldKind scaffoldKind(
            String kind,
            String service,
            List<String> defaultInputs,
            List<String> defaultOutputs,
            List<String> defaultTargets,
            String summary,
            String exampleCommand) {
        return new ScaffoldKind(
                kind,
                service,
                defaultInputs,
                defaultOutputs,
                defaultTargets,
                summary,
                exampleCommand);
    }

    public record ScaffoldKind(
            String kind,
            String service,
            List<String> defaultInputs,
            List<String> defaultOutputs,
            List<String> defaultTargets,
            String summary,
            String exampleCommand) {
        public ScaffoldKind {
            defaultInputs = defaultInputs == null ? List.of() : List.copyOf(defaultInputs);
            defaultOutputs = defaultOutputs == null ? List.of() : List.copyOf(defaultOutputs);
            defaultTargets = defaultTargets == null ? List.of() : List.copyOf(defaultTargets);
            summary = summary == null ? "" : summary;
            exampleCommand = exampleCommand == null ? "" : exampleCommand;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("kind", kind);
            value.put("service", service);
            value.put("default_inputs", defaultInputs);
            value.put("default_outputs", defaultOutputs);
            value.put("default_targets", defaultTargets);
            value.put("summary", summary);
            value.put("example", exampleCommand);
            return value;
        }
    }
}
