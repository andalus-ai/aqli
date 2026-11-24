package tech.kayys.aqli.cli.commands.features.scaffold;

import tech.kayys.aqli.cli.commands.features.FeatureAdapterKinds;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministically renders source files and metadata for an extension scaffold.
 * This class has no filesystem or terminal side effects.
 */
public final class ScaffoldContentGenerator {

    private static final String SERVICE_ENTRY = "META-INF/services/tech.kayys.aqli.spi.pipeline.ModelPipeline";
    private static final String ADAPTER_SERVICE_ENTRY = "META-INF/services/tech.kayys.aqli.spi.feature.FeatureAdapter";
    private static final String MODEL_FAMILY_KIND = "model-family";
    private static final String MODEL_FAMILY_SERVICE_ENTRY = "META-INF/services/tech.kayys.aljabr.spi.model.ModelFamilyPlugin";
    private static final String AQLI_PLUGIN_SERVICE_ENTRY = "META-INF/services/tech.kayys.aqli.spi.plugin.AqliPlugin";
    private static final String MANIFEST_ENTRY = "META-INF/aqli-extension.json";
    private static final String PLUGIN_DESCRIPTOR_ENTRY = "plugin.json";

    private final String aqliVersion;

    public ScaffoldContentGenerator(String aqliVersion) {
        this.aqliVersion = aqliVersion == null || aqliVersion.isBlank() ? "dev" : aqliVersion;
    }

    public List<GeneratedFile> generate(ScaffoldDefinition scaffold) {
        List<GeneratedFile> files = new ArrayList<>();
        files.add(new GeneratedFile("settings.gradle.kts", settingsGradle(scaffold)));
        files.add(new GeneratedFile("gradle.properties", gradleProperties()));
        files.add(new GeneratedFile("lib/build.gradle.kts", featureBuildGradle(scaffold)));
        files.add(new GeneratedFile("lib/src/main/java/" + scaffold.packageName().replace('.', '/')
                + "/" + scaffold.className() + ".java",
                scaffold.modelFamily() ? modelFamilyPluginJava(scaffold)
                        : scaffold.pipeline() ? featurePipelineJava(scaffold) : featureAdapterJava(scaffold)));
        files.add(new GeneratedFile("lib/src/main/resources/" + serviceEntry(scaffold),
                scaffold.fqcn() + System.lineSeparator()));
        if (scaffold.modelFamily()) {
            files.add(new GeneratedFile("lib/src/main/resources/" + AQLI_PLUGIN_SERVICE_ENTRY,
                    scaffold.fqcn() + System.lineSeparator()));
            files.add(new GeneratedFile("lib/src/main/resources/" + PLUGIN_DESCRIPTOR_ENTRY,
                    modelFamilyPluginDescriptor(scaffold)));
        }
        files.add(new GeneratedFile("lib/src/main/resources/" + MANIFEST_ENTRY, featureManifest(scaffold)));
        files.add(new GeneratedFile("README.md", scaffoldReadme(scaffold)));
        return List.copyOf(files);
    }

    public String buildCommand(ScaffoldDefinition scaffold) {
        return scaffoldBuildCommand(scaffold);
    }

    public record GeneratedFile(String relativePath, String content) { }

    public record ScaffoldDefinition(
            Path root, String featureId, String kind, String capabilityId, String pipelineId,
            String displayName, String packageName, String className, String family,
            List<String> pipelineInputs, List<String> pipelineOutputs,
            List<String> adapterInputs, List<String> adapterOutputs, List<String> tags,
            String providerHint, List<String> formatHints, List<String> targets,
            List<String> modelTypes, List<String> architectureClassNames,
            List<String> modelFamilyCapabilities, String tokenizerKind,
            String tokenizerMetadataStatus, String tokenizerMetadataPendingReason,
            String bundleProfile, Path aqliPath) {
        public ScaffoldDefinition {
            pipelineInputs = immutable(pipelineInputs);
            pipelineOutputs = immutable(pipelineOutputs);
            adapterInputs = immutable(adapterInputs);
            adapterOutputs = immutable(adapterOutputs);
            tags = immutable(tags);
            formatHints = immutable(formatHints);
            targets = immutable(targets);
            modelTypes = immutable(modelTypes);
            architectureClassNames = immutable(architectureClassNames);
            modelFamilyCapabilities = immutable(modelFamilyCapabilities);
        }
        private static List<String> immutable(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
        boolean modelFamily() { return MODEL_FAMILY_KIND.equals(kind); }
        boolean pipeline() { return !modelFamily() && FeatureAdapterKinds.PIPELINE.equals(kind); }
        boolean tokenizerMetadataReady() { return "ready".equals(tokenizerMetadataStatus); }
        boolean tokenizerMetadataPending() { return "pending".equals(tokenizerMetadataStatus); }
        String fqcn() { return packageName + "." + className; }
        String formatHint() { return formatHints.isEmpty() ? "" : formatHints.get(0); }
        List<String> inputNames() { return pipeline() ? pipelineInputs : adapterInputs; }
        List<String> outputNames() { return pipeline() ? pipelineOutputs : adapterOutputs; }
    }


private static String settingsGradle(ScaffoldDefinition scaffold) {
        StringBuilder text = new StringBuilder();
        text.append("rootProject.name = \"").append(escapeGradle(scaffold.featureId())).append("\"\n");
        text.append("include(\":lib\")\n");
        if (scaffold.aqliPath() != null) {
            String relative = relativePath(scaffold.root(), scaffold.aqliPath());
            text.append("\n");
            text.append("val aqliPath = file(\"").append(escapeGradle(relative)).append("\")\n");
            text.append("if (aqliPath.exists()) {\n");
            text.append("    includeBuild(aqliPath)\n");
            text.append("}\n");
        } else {
            text.append("\n");
            text.append("// Pass --aqli-path when scaffolding to consume a local Aqli checkout as a composite build.\n");
            text.append("// Otherwise publish Aqli SPI to mavenLocal before building this feature.\n");
        }
        return text.toString();
    }

private static String gradleProperties() {
        return """
                org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
                org.gradle.parallel=true
                """;
    }

private static String featureBuildGradle(ScaffoldDefinition scaffold) {
        return """
                plugins {
                    `java-library`
                }

                group = "tech.kayys.aqli.extensions"
                version = "0.1.0-SNAPSHOT"

                repositories {
                    mavenCentral()
                    mavenLocal()
                }

                dependencies {
                    api("tech.kayys.aqli:aqli-spi:0.1.0-SNAPSHOT")
                    api("tech.kayys.aqli:aqli-spi-provider:0.1.0-SNAPSHOT")
                    api("tech.kayys.aqli:aqli-spi-inference:0.1.0-SNAPSHOT")
                    api("tech.kayys.aqli:aqli-spi-multimodal:0.1.0-SNAPSHOT")
                    api("tech.kayys.aqli:aqli-spi-model:0.1.0-SNAPSHOT")
                    implementation("io.smallrye.reactive:mutiny:2.5.5")
                    compileOnly("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")

                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(25)
                    }
                }

                tasks.withType<JavaCompile>().configureEach {
                    options.encoding = "UTF-8"
                    options.compilerArgs.add("--enable-preview")
                    options.compilerArgs.add("--add-modules=jdk.incubator.vector")
                }

                tasks.withType<Test>().configureEach {
                    useJUnitPlatform()
                    jvmArgs("--enable-preview", "--add-modules=jdk.incubator.vector")
                }

                tasks.jar {
                    archiveBaseName = "%s"
                }
                """.formatted(scaffold.featureId());
    }

private static String serviceEntry(ScaffoldDefinition scaffold) {
        if (scaffold.modelFamily()) {
            return MODEL_FAMILY_SERVICE_ENTRY;
        }
        return scaffold.pipeline() ? SERVICE_ENTRY : ADAPTER_SERVICE_ENTRY;
    }

private static String modelFamilyPluginDescriptor(ScaffoldDefinition scaffold) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", "model-family/" + scaffold.capabilityId());
        descriptor.put("name", scaffold.displayName());
        descriptor.put("version", "0.1.0-SNAPSHOT");
        descriptor.put("description", "Out-of-core Aqli model-family plugin for " + scaffold.capabilityId());
        descriptor.put("vendor", "External");
        descriptor.put("mainClass", scaffold.fqcn());
        descriptor.put("dependencies", List.of());
        descriptor.put("optionalDependencies", List.of());
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("extensionPoint", MODEL_FAMILY_KIND);
        properties.put("bundleProfile", scaffold.bundleProfile());
        properties.put("families", scaffold.modelTypes());
        if (scaffold.tokenizerMetadataReady()) {
            properties.put("tokenizerKind", scaffold.tokenizerKind());
            properties.put("tokenizerKinds", List.of(scaffold.tokenizerKind()));
        } else if (scaffold.tokenizerMetadataPending()) {
            properties.put("tokenizerMetadataStatus", "pending");
            properties.put("tokenizerMetadataPendingReason", scaffold.tokenizerMetadataPendingReason());
        }
        descriptor.put("properties", properties);
        try {
            return new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(descriptor) + System.lineSeparator();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write plugin descriptor JSON", e);
        }
    }

private static String featurePipelineJava(ScaffoldDefinition scaffold) {
        String inputs = modalityJavaList(scaffold.pipelineInputs());
        String outputs = modalityJavaList(scaffold.pipelineOutputs());
        String tags = stringJavaList(scaffold.tags());
        String provider = scaffold.providerHint().isBlank() ? "" : scaffold.providerHint();
        String format = scaffold.formatHint().isBlank() ? "" : scaffold.formatHint();
        return """
                package %s;

                import io.smallrye.mutiny.Uni;
                import jakarta.enterprise.context.ApplicationScoped;
                import tech.kayys.aqli.spi.inference.InferenceResponse;
                                                import tech.kayys.aqli.spi.pipeline.ModelPipelineRequest;
                
                import java.time.Duration;
                import java.util.List;
                import java.util.Locale;
                import java.util.Map;

                @ApplicationScoped
                public class %s implements ModelPipeline {
                    public static final String PIPELINE_ID = "%s";

                    @Override
                    public String id() {
                        return PIPELINE_ID;
                    }

                    @Override
                    public PipelineDescriptor descriptor() {
                        return new PipelineDescriptor(
                                PIPELINE_ID,
                                "%s",
                                "0.1.0",
                                "%s",
                                %s,
                                %s,
                                %s,
                                Map.of(
                                        "source", "external-extension",
                                        "kind", "feature-pipeline",
                                        "provider", "%s",
                                        "format", "%s"));
                    }

                    @Override
                    public int priority() {
                        return 50;
                    }

                    @Override
                    public boolean supports(ModelPipelineRequest request) {
                        if (request == null) {
                            return false;
                        }
                        if (matches(request.parameterText("pipeline").orElse(null))
                                || matches(request.parameterText("feature").orElse(null))
                                || matches(request.factText("pipeline").orElse(null))
                                || matches(request.factText("pipeline_id").orElse(null))) {
                            return true;
                        }
                        return request.parameterBoolean("feature_pipeline") && providerMatches(request) && formatMatches(request);
                    }

                    @Override
                    public Uni<InferenceResponse> infer(ModelPipelineRequest request) {
                        return Uni.createFrom().item(() -> {
                            long started = System.nanoTime();
                            Map<String, Object> metadata = Map.of(
                                    "pipeline", PIPELINE_ID,
                                    "feature", "%s",
                                    "provider", request.providerId() == null ? "" : request.providerId(),
                                    "format", request.factText("format").orElse(""));
                            return InferenceResponse.builder()
                                    .requestId(request.request().getRequestId())
                                    .model(request.request().getModel())
                                    .content("Extension pipeline '%s' is scaffolded. Replace this response with real orchestration.")
                                    .durationMs(Duration.ofNanos(System.nanoTime() - started).toMillis())
                                    .metadata(metadata)
                                    .build();
                        });
                    }

                    private boolean providerMatches(ModelPipelineRequest request) {
                        String expected = "%s";
                        return expected.isBlank()
                                || expected.equalsIgnoreCase(String.valueOf(request.providerId()));
                    }

                    private boolean formatMatches(ModelPipelineRequest request) {
                        String expected = "%s";
                        return expected.isBlank()
                                || request.factText("format")
                                        .map(value -> expected.equalsIgnoreCase(value))
                                        .orElse(false);
                    }

                    private boolean matches(String value) {
                        return value != null
                                && PIPELINE_ID.equals(value.trim().toLowerCase(Locale.ROOT));
                    }
                }
                """.formatted(
                scaffold.packageName(),
                scaffold.className(),
                scaffold.pipelineId(),
                escapeJava(scaffold.displayName()),
                scaffold.family(),
                inputs,
                outputs,
                tags,
                escapeJava(provider),
                escapeJava(format),
                scaffold.featureId(),
                scaffold.pipelineId(),
                escapeJava(provider),
                escapeJava(format));
    }

private static String featureAdapterJava(ScaffoldDefinition scaffold) {
        String inputs = stringJavaList(scaffold.inputNames());
        String outputs = stringJavaList(scaffold.outputNames());
        String formats = stringJavaList(scaffold.formatHints());
        String targets = stringJavaList(scaffold.targets());
        String tags = stringJavaList(scaffold.tags());
        String provider = scaffold.providerHint().isBlank() ? "" : scaffold.providerHint();
        String format = scaffold.formatHint().isBlank() ? "" : scaffold.formatHint();
        return """
                package %s;

                import jakarta.enterprise.context.ApplicationScoped;
                import tech.kayys.aqli.spi.feature.FeatureAdapter;
                import tech.kayys.aqli.spi.feature.FeatureAdapterDescriptor;

                import java.util.List;
                import java.util.Map;

                @ApplicationScoped
                public class %s implements FeatureAdapter {
                    public static final String ADAPTER_ID = "%s";

                    @Override
                    public String id() {
                        return ADAPTER_ID;
                    }

                    @Override
                    public FeatureAdapterDescriptor descriptor() {
                        return new FeatureAdapterDescriptor(
                                ADAPTER_ID,
                                "%s",
                                "%s",
                                "0.1.0",
                                "%s",
                                %s,
                                %s,
                                %s,
                                %s,
                                %s,
                                Map.of(
                                        "source", "external-extension",
                                        "provider", "%s",
                                        "format", "%s"));
                    }

                    @Override
                    public int priority() {
                        return 50;
                    }
                }
                """.formatted(
                scaffold.packageName(),
                scaffold.className(),
                scaffold.capabilityId(),
                scaffold.kind(),
                escapeJava(scaffold.displayName()),
                scaffold.family(),
                inputs,
                outputs,
                formats,
                targets,
                tags,
                escapeJava(provider),
                escapeJava(format));
    }

private static String modelFamilyPluginJava(ScaffoldDefinition scaffold) {
        String capabilities = modelFamilyCapabilityJavaList(scaffold.modelFamilyCapabilities());
        String modelTypes = stringJavaList(scaffold.modelTypes());
        String architectureClasses = stringJavaList(scaffold.architectureClassNames());
        String tokenizerDescriptors = modelFamilyTokenizerDescriptorJava(scaffold);
        String metadata = modelFamilyMetadataJava(scaffold);
        return """
                package %s;

                                import tech.kayys.aljabr.spi.model.ModelFamilyDescriptor;
                import tech.kayys.aljabr.spi.model.ModelFamilyPlugin;
                import tech.kayys.aljabr.spi.model.ModelTokenizerDescriptor;

                import java.util.List;
                import java.util.Map;

                public final class %s implements ModelFamilyPlugin {
                    public static final String FAMILY_ID = "%s";

                    @Override
                    public ModelFamilyDescriptor descriptor() {
                        return new ModelFamilyDescriptor(
                                FAMILY_ID,
                                "%s",
                                %s,
                                %s,
                                %s,
                                %s);
                    }

                    @Override
                    public List<ModelTokenizerDescriptor> tokenizerDescriptors() {
                        return %s;
                    }
                }
                """.formatted(
                scaffold.packageName(),
                scaffold.className(),
                scaffold.capabilityId(),
                escapeJava(scaffold.displayName()),
                modelTypes,
                architectureClasses,
                capabilities,
                metadata,
                tokenizerDescriptors);
    }

private static String featureManifest(ScaffoldDefinition scaffold) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema_version", 1);
        manifest.put("id", scaffold.featureId());
        manifest.put("name", scaffold.displayName());
        manifest.put("version", "0.1.0-SNAPSHOT");
        manifest.put("description", scaffold.pipeline()
                ? "Out-of-core Aqli extension pipeline"
                : "Out-of-core Aqli extension adapter");
        manifest.put("source", "external-extension");
        manifest.put("requires", Map.of("aqli_spi", aqliVersion));
        manifest.put("loading", Map.of(
                "jvm", "service-loader",
                "native_image", "ahead-of-time",
                "native_image_note", "include this extension jar on the native-image build classpath"));
        if (scaffold.modelFamily()) {
            manifest.put("services", Map.of("model_family_plugins", List.of(scaffold.fqcn())));
            Map<String, Object> family = new LinkedHashMap<>();
            family.put("id", scaffold.capabilityId());
            family.put("class", scaffold.fqcn());
            family.put("model_types", scaffold.modelTypes());
            family.put("architecture_classes", scaffold.architectureClassNames());
            family.put("capabilities", scaffold.modelFamilyCapabilities());
            family.put("tokenizer_metadata_status", scaffold.tokenizerMetadataStatus());
            if (scaffold.tokenizerMetadataReady()) {
                family.put("tokenizer_kind", scaffold.tokenizerKind());
                family.put("tokenizer_kinds", List.of(scaffold.tokenizerKind()));
            } else if (scaffold.tokenizerMetadataPending()) {
                family.put("tokenizer_metadata_pending_reason", scaffold.tokenizerMetadataPendingReason());
            }
            family.put("bundle_profile", scaffold.bundleProfile());
            manifest.put("model_families", List.of(family));
        } else if (scaffold.pipeline()) {
            manifest.put("services", Map.of("model_pipelines", List.of(scaffold.fqcn())));
            Map<String, Object> pipeline = new LinkedHashMap<>();
            pipeline.put("id", scaffold.pipelineId());
            pipeline.put("class", scaffold.fqcn());
            pipeline.put("family", scaffold.family());
            pipeline.put("inputs", scaffold.inputNames());
            pipeline.put("outputs", scaffold.outputNames());
            if (!scaffold.providerHint().isBlank()) {
                pipeline.put("provider", scaffold.providerHint());
            }
            if (!scaffold.formatHint().isBlank()) {
                pipeline.put("format", scaffold.formatHint());
            }
            manifest.put("pipelines", List.of(pipeline));
        } else {
            manifest.put("services", Map.of("feature_adapters", List.of(scaffold.fqcn())));
            Map<String, Object> capability = new LinkedHashMap<>();
            capability.put("id", scaffold.capabilityId());
            capability.put("kind", scaffold.kind());
            capability.put("class", scaffold.fqcn());
            capability.put("family", scaffold.family());
            capability.put("inputs", scaffold.inputNames());
            capability.put("outputs", scaffold.outputNames());
            if (!scaffold.formatHints().isEmpty()) {
                capability.put("formats", scaffold.formatHints());
            }
            if (!scaffold.targets().isEmpty()) {
                capability.put("targets", scaffold.targets());
            }
            if (!scaffold.providerHint().isBlank()) {
                capability.put("provider", scaffold.providerHint());
            }
            manifest.put("capabilities", List.of(capability));
        }
        try {
            return new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(manifest) + System.lineSeparator();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write scaffold manifest JSON", e);
        }
    }

private static String scaffoldReadme(ScaffoldDefinition scaffold) {
        if (scaffold.modelFamily()) {
            return """
                    # %s

                    Out-of-core Aqli model-family plugin.

                    This plugin implements `%s`, publishes reusable model metadata through `%s`,
                    and is visible to the core plugin manager through `%s` plus root `%s`:

                    ```text
                    %s
                    %s
                    %s
                    ```

                    ## Build

                    ```bash
                    %s
                    ```

                    ## Attach During Development

                    ```bash
                    aqli modules --doctor --plugin-dir lib/build/libs
                    aqli show <model-or-path> --plugin-dir lib/build/libs --json
                    ```

                    ## Install For Local Reuse

                    ```bash
                    aqli extensions inspect lib/build/libs/%s-0.1.0-SNAPSHOT.jar --strict
                    mkdir -p ~/.aqli/plugins
                    aqli extensions install lib/build/libs/%s-0.1.0-SNAPSHOT.jar --plugin-dir ~/.aqli/plugins --force
                    AQLI_PLUGIN_DIRS=~/.aqli/plugins aqli modules --doctor
                    ```

                    ## Tokenizer Metadata Checks

                    ```bash
                    aqli extensions inspect lib/build/libs/%s-0.1.0-SNAPSHOT.jar --strict --json
                    aqli extensions doctor --plugin-dir lib/build/libs --json
                    ```

                    Before install, JSON `jar.artifact_tokenizer_metadata.tokenizer_metadata_status`
                    reports `ready` or `pending`. After install, `install_lock.tokenizer_metadata_status`
                    and doctor `tokenizer_lock_status` show what production tooling will enforce.

                    Pending tokenizer metadata is valid for development and research scaffolds, but keep
                    pending-tokenizer plugins out of production inference presets until the tokenizer adapter
                    is ready.

                    ## Detach

                    ```bash
                    aqli extensions detach %s --plugin-dir ~/.aqli/plugins
                    ```

                    ## Family Contract

                    Model-family id: `%s`

                    Model types: `%s`

                    Architecture classes: `%s`

                    Capabilities: `%s`

                    Tokenizer metadata: `%s`

                    %s

                    Start with tokenizer/config metadata only. Add direct SafeTensor architecture adapters later
                    when the runtime implementation is ready and validated by `aqli modules --doctor`.
                    """.formatted(
                    scaffold.displayName(),
                    scaffold.fqcn(),
                    MODEL_FAMILY_SERVICE_ENTRY,
                    AQLI_PLUGIN_SERVICE_ENTRY,
                    PLUGIN_DESCRIPTOR_ENTRY,
                    MODEL_FAMILY_SERVICE_ENTRY,
                    AQLI_PLUGIN_SERVICE_ENTRY,
                    PLUGIN_DESCRIPTOR_ENTRY,
                    scaffoldBuildCommand(scaffold),
                    scaffold.featureId(),
                    scaffold.featureId(),
                    scaffold.featureId(),
                    scaffold.capabilityId(),
                    scaffold.capabilityId(),
                    String.join(", ", scaffold.modelTypes()),
                    String.join(", ", scaffold.architectureClassNames()),
                    String.join(", ", scaffold.modelFamilyCapabilities()),
                    modelFamilyTokenizerMetadataSummary(scaffold),
                    modelFamilyTokenizerMetadataReadmeNote(scaffold));
        }
        if (!scaffold.pipeline()) {
            return """
                    # %s

                    Out-of-core Aqli %s extension adapter.

                    ## Build

                    ```bash
                    %s
                    ```

                    ## Validate And Install

                    ```bash
                    aqli extensions inspect lib/build/libs/%s-0.1.0-SNAPSHOT.jar --strict
                    aqli extensions install lib/build/libs/%s-0.1.0-SNAPSHOT.jar --force
                    aqli extensions doctor --strict
                    aqli extensions --details --paths
                    ```

                    ## Adapter Contract

                    This project publishes `%s` through:

                    ```text
                    %s
                    ```

                    The manifest declares capability `%s` with kind `%s`. Domain registries for
                    training, optimization, quantization, backend, exporter, dataset, evaluator, or
                    other adapter families can discover this service and route it into their own APIs.

                    The generated manifest includes `requires.aqli_spi`, service metadata, and an
                    ahead-of-time loading note. `aqli extensions doctor --strict` checks compatibility
                    metadata before the jar is used at runtime, and `aqli extensions index` emits an
                    inventory for JVM launchers or native-image build inputs.
                    """.formatted(
                    scaffold.displayName(),
                    scaffold.kind(),
                    scaffoldBuildCommand(scaffold),
                    scaffold.featureId(),
                    scaffold.featureId(),
                    scaffold.fqcn(),
                    ADAPTER_SERVICE_ENTRY,
                    scaffold.capabilityId(),
                    scaffold.kind());
        }
        return """
                # %s

                Out-of-core Aqli extension pipeline.

                ## Build

                ```bash
                %s
                ```

                ## Validate And Install

                ```bash
                aqli extensions inspect lib/build/libs/%s-0.1.0-SNAPSHOT.jar --strict
                aqli extensions install lib/build/libs/%s-0.1.0-SNAPSHOT.jar --force
                aqli extensions doctor --strict
                ```

                ## Run During Development

                ```bash
                aqli run --model <model> --pipeline %s --prompt "hello"
                ```

                The generated pipeline is intentionally conservative. It only handles a request when `--pipeline %s`
                selects it, or after you add model-specific detection in `supports(...)`.

                The generated manifest includes `requires.aqli_spi`, service metadata, and an
                ahead-of-time loading note. `aqli extensions doctor --strict` checks compatibility
                metadata before the jar is used at runtime, and `aqli extensions index` emits an
                inventory for JVM launchers or native-image build inputs.
                """.formatted(
                scaffold.displayName(),
                scaffoldBuildCommand(scaffold),
                scaffold.featureId(),
                scaffold.featureId(),
                scaffold.pipelineId(),
                scaffold.pipelineId());
    }

private static String scaffoldBuildCommand(ScaffoldDefinition scaffold) {
        if (scaffold.aqliPath() == null) {
            return "gradle :lib:build";
        }
        String relative = relativePath(scaffold.root(), scaffold.aqliPath()).replace('\\', '/');
        return relative + "/gradlew -p . :lib:build";
    }

private static String modelFamilyCapabilityJavaList(List<String> capabilities) {
        List<String> normalized = capabilities == null || capabilities.isEmpty() ? List.of("TOKENIZER") : capabilities;
        return "List.of(" + normalized.stream()
                .map(capability -> "ModelFamilyCapability." + capability)
                .collect(Collectors.joining(", ")) + ")";
    }

private static String modelFamilyMetadataJava(ScaffoldDefinition scaffold) {
        List<String> entries = new ArrayList<>();
        entries.add("\"bundle_profile\", \"" + escapeJava(scaffold.bundleProfile()) + "\"");
        entries.add("\"origin\", \"external/model-family\"");
        if (scaffold.tokenizerMetadataPending()) {
            entries.add("\"tokenizer_metadata_status\", \"pending\"");
            entries.add("\"tokenizer_metadata_pending_reason\", \""
                    + escapeJava(scaffold.tokenizerMetadataPendingReason()) + "\"");
        } else if (scaffold.tokenizerMetadataReady()) {
            entries.add("\"tokenizer_metadata_status\", \"ready\"");
        }
        entries.add("\"version\", \"0.1.0-SNAPSHOT\"");
        return "Map.of(" + String.join(", ", entries) + ")";
    }

private static String modelFamilyTokenizerMetadataSummary(ScaffoldDefinition scaffold) {
        if (scaffold.tokenizerMetadataPending()) {
            return "pending (" + scaffold.tokenizerMetadataPendingReason() + ")";
        }
        return "ready (" + scaffold.tokenizerKind() + ")";
    }

private static String modelFamilyTokenizerMetadataReadmeNote(ScaffoldDefinition scaffold) {
        if (scaffold.tokenizerMetadataPending()) {
            return "Pending tokenizer metadata intentionally omits tokenizer descriptors until the adapter contract is implemented.";
        }
        return "Ready tokenizer metadata publishes reusable tokenizer descriptors for runtime discovery.";
    }

private static String modelFamilyTokenizerDescriptorJava(ScaffoldDefinition scaffold) {
        if (scaffold.tokenizerMetadataPending()) {
            return "List.of()";
        }
        return switch (scaffold.tokenizerKind()) {
            case "hf-bpe" -> "List.of(ModelTokenizerDescriptor.huggingFaceBpe(FAMILY_ID + \"-tokenizer\"))";
            case "sentencepiece" -> "List.of(ModelTokenizerDescriptor.sentencePieceBpe(FAMILY_ID + \"-tokenizer\"))";
            case "wordpiece" -> "List.of(ModelTokenizerDescriptor.wordPiece(FAMILY_ID + \"-tokenizer\"))";
            case "custom" -> "List.of(new ModelTokenizerDescriptor("
                    + "FAMILY_ID + \"-tokenizer\", "
                    + "tech.kayys.aljabr.spi.model.ModelTokenizerKind.CUSTOM, "
                    + "List.of(), "
                    + "Map.of()))";
            case "none" -> "List.of()";
            default -> throw new IllegalArgumentException("Unknown tokenizer kind: " + scaffold.tokenizerKind());
        };
    }

private static String modalityJavaList(List<String> modalities) {
        return "List.of(" + modalities.stream()
                .map(type -> "ModalityType." + type)
                .reduce((left, right) -> left + ", " + right)
                .orElse("ModalityType.TEXT") + ")";
    }

private static String stringJavaList(List<String> values) {
        return "List.of(" + values.stream()
                .map(value -> "\"" + escapeJava(value) + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("") + ")";
    }

private static String relativePath(Path root, Path target) {
        try {
            return root.relativize(target).toString();
        } catch (Exception ignored) {
            return target.toString();
        }
    }

private static String escapeJava(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

private static String escapeGradle(String value) {
        return escapeJava(value).replace("$", "\\$");
    }

}
