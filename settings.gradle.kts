rootProject.name = "aqli-engine"

// Aljabr is the canonical successor to the former Alkhawarizm project.
// Prefer a checkout inside the CI workspace, while supporting sibling checkouts locally.
val aljabrDir = listOf(file("aljabr"), file("../aljabr"))
    .firstOrNull { candidate ->
        candidate.exists() &&
                (candidate.resolve("settings.gradle.kts").isFile || candidate.resolve("settings.gradle").isFile)
    }
if (aljabrDir != null) {
    includeBuild(aljabrDir)
}

// Suling provides the optional Java audio codec facade used by audio-capable runners.
// Resolve it from source in CI and local multi-repository workspaces instead of
// relying on a separately published tech.kayys:suling snapshot.
val sulingDir = listOf(file("suling"), file("../suling"))
    .firstOrNull { candidate ->
        candidate.exists() &&
                (candidate.resolve("settings.gradle.kts").isFile || candidate.resolve("settings.gradle").isFile)
    }
if (sulingDir != null) {
    includeBuild(sulingDir)
}

// Include alkhawarizm and tafkir as composite builds if their directories exist locally (during local multi-project development)
val alkhawarizmDir = file("../alkhawarizm")
if (alkhawarizmDir.exists() && (alkhawarizmDir.resolve("build.gradle.kts").isFile || alkhawarizmDir.resolve("build.gradle").isFile || alkhawarizmDir.resolve("settings.gradle.kts").isFile || alkhawarizmDir.resolve("settings.gradle").isFile)) {
    includeBuild("../alkhawarizm")
}

val tafkirDir = file("../tafkir")
if (tafkirDir.exists() && (tafkirDir.resolve("build.gradle.kts").isFile || tafkirDir.resolve("build.gradle").isFile || tafkirDir.resolve("settings.gradle.kts").isFile || tafkirDir.resolve("settings.gradle").isFile)) {
    includeBuild("../tafkir")
}
// Keep logical grouping projects stable when optional modules come from
// different roots (for example, built-in plugins and sibling Wayang modules).
val optionalParentProjectDirs = mutableMapOf<String, java.io.File>()

fun includeOptionalProject(projectPath: String, vararg candidatePaths: String) {
    // Resolve the leaf first. Intermediate Gradle projects must point at real
    // ancestor directories of the selected module, not guessed paths such as
    // framework/tokenizer that may not exist in this repository layout.
    val projectDir = candidatePaths
        .map { file(it) }
        .firstOrNull { candidate ->
            candidate.isDirectory &&
                    (candidate.resolve("build.gradle.kts").isFile ||
                            candidate.resolve("build.gradle").isFile)
        }
        ?: return

    val parts = projectPath.split(":")
    var currentPath = ""
    for (i in 0 until parts.size - 1) {
        val part = parts[i]
        currentPath = if (currentPath.isEmpty()) part else "$currentPath:$part"
        include(currentPath)

        if (currentPath !in optionalParentProjectDirs) {
            // ':tokenizer' is a logical grouping project; its module lives under
            // framework/core, so use framework as its stable grouping directory.
            // This also avoids sharing ':core'’s directory.
            var parentDir = if (parts[0] == "tokenizer" && i == 0) {
                file("framework")
            } else {
                projectDir
            }
            if (!(parts[0] == "tokenizer" && i == 0)) {
                repeat(parts.size - i - 1) {
                    parentDir = parentDir.parentFile
                        ?: throw GradleException("Cannot resolve parent directory for Gradle project ':$currentPath'")
                }
            }
            if (!parentDir.isDirectory) {
                throw GradleException(
                    "Cannot include Gradle project ':$currentPath': mapped directory '$parentDir' does not exist"
                )
            }
            optionalParentProjectDirs[currentPath] = parentDir
            project(":$currentPath").projectDir = parentDir
        }
    }

    include(projectPath)
    project(":$projectPath").projectDir = projectDir
}

// Optional core and repository projects
includeOptionalProject("adapter:aqli-adapter", "runtime/adapter/aqli-adapter")
includeOptionalProject("core:aqli-core", "framework/core/aqli-core")
includeOptionalProject("tokenizer:aqli-tokenizer-core", "framework/core/aqli-tokenizer-core")
includeOptionalProject("repository:aqli-model-database", "runtime/repository/aqli-model-database")
includeOptionalProject("repository:aqli-model-repo-common", "runtime/repository/aqli-model-repo-common")
includeOptionalProject("repository:aqli-model-repo-hf", "runtime/repository/aqli-model-repo-hf")
includeOptionalProject("repository:aqli-model-repo-kaggle", "runtime/repository/aqli-model-repo-kaggle")
includeOptionalProject("repository:aqli-model-repo-local", "runtime/repository/aqli-model-repo-local")
includeOptionalProject("spi:aqli-spi-runner", "framework/spi/aqli-spi-runner")
includeOptionalProject("observability:aqli-observability", "framework/observability/aqli-observability")
// Dynamically include model projects if they exist (avoid hard failure when some model dirs are missing)
val staticallyIncludedModelProjects = setOf<String>()

// Include statically listed ones only if their directories exist (also check alkhawarizm/models)
staticallyIncludedModelProjects.forEach { name ->
    val alkhawarizmAlt = "../alkhawarizm/models/alkhawarizm-model-${name.removePrefix("aqli-model-")}"
    includeOptionalProject("models:$name", "models/$name", "../alkhawarizm/models/$name", alkhawarizmAlt)
}

// Auto-include any additional model projects present under any 'models' directories in the repository
val discoveredModelDirs = mutableSetOf<java.io.File>()
file(".").walkTopDown().maxDepth(5).forEach { f ->
    if (f.isDirectory && f.name == "models") {
        f.listFiles { candidate ->
            candidate.isDirectory &&
                    candidate.name.startsWith("aqli-model-") &&
                    candidate.name !in staticallyIncludedModelProjects &&
                    (candidate.resolve("build.gradle.kts").isFile || candidate.resolve("build.gradle").isFile)
        }?.forEach { discoveredModelDirs.add(it.absoluteFile) }
    }
}

discoveredModelDirs.sortedBy { it.name }.forEach { modelProject ->
    val logicalPath = "models:${modelProject.name}"
    includeOptionalProject(logicalPath, modelProject.absolutePath)
}

// Optional optimization plugins
// includeOptionalProject("optimization:aqli-plugin-elastic-ep", "runtime/optimization/aqli-plugin-elastic-ep")
// includeOptionalProject("optimization:aqli-plugin-evicpress", "runtime/optimization/aqli-plugin-evicpress")
// includeOptionalProject("optimization:aqli-plugin-fa3", "runtime/optimization/aqli-plugin-fa3")
// includeOptionalProject("optimization:aqli-plugin-fa4", "runtime/optimization/aqli-plugin-fa4")
// includeOptionalProject("optimization:aqli-plugin-hybrid-attn", "runtime/optimization/aqli-plugin-hybrid-attn")
includeOptionalProject("optimization:aqli-plugin-kv-cache", "runtime/optimization/aqli-plugin-kv-cache")
// includeOptionalProject("optimization:aqli-plugin-paged-attention", "runtime/optimization/aqli-plugin-paged-attention")
// includeOptionalProject("optimization:aqli-plugin-perfmode", "runtime/optimization/aqli-plugin-perfmode")
// includeOptionalProject("optimization:aqli-plugin-prefill-decode", "runtime/optimization/aqli-plugin-prefill-decode")
// includeOptionalProject("optimization:aqli-plugin-prompt-cache", "runtime/optimization/aqli-plugin-prompt-cache")
// includeOptionalProject("optimization:aqli-plugin-qlora", "runtime/optimization/aqli-plugin-qlora")
// includeOptionalProject("optimization:aqli-plugin-wait-scheduler", "runtime/optimization/aqli-plugin-wait-scheduler")
// includeOptionalProject("optimization:aqli-plugin-weight-offload", "runtime/optimization/aqli-plugin-weight-offload")

// Optional plugins
includeOptionalProject("plugins:aqli-plugin-content-safety", "runtime/plugins/aqli-plugin-content-safety")
includeOptionalProject("plugins:aqli-plugin-mcp", "runtime/plugins/aqli-plugin-mcp")
includeOptionalProject("plugins:aqli-plugin-model-router", "runtime/plugins/aqli-plugin-model-router")
includeOptionalProject("plugins:wayang-plugin-openai", "../wayang/modules/provider/wayang-plugin-openai")
includeOptionalProject("plugins:wayang-plugin-anthropic", "../wayang/modules/provider/wayang-plugin-anthropic")
includeOptionalProject("plugins:wayang-plugin-gemini", "../wayang/modules/provider/wayang-plugin-gemini")
includeOptionalProject("plugins:wayang-plugin-cerebras", "../wayang/modules/provider/wayang-plugin-cerebras")
includeOptionalProject("plugins:wayang-plugin-mistral", "../wayang/modules/provider/wayang-plugin-mistral")
// includeOptionalProject("plugins:aqli-plugin-observability", "runtime/plugins/aqli-plugin-observability")
// includeOptionalProject("plugins:aqli-plugin-pii-redaction", "runtime/plugins/aqli-plugin-pii-redaction")
// includeOptionalProject("plugins:aqli-plugin-prompt", "runtime/plugins/aqli-plugin-prompt")
// includeOptionalProject("plugins:aqli-plugin-quota", "runtime/plugins/aqli-plugin-quota")
// includeOptionalProject("plugins:aqli-plugin-rag", "runtime/plugins/aqli-plugin-rag")
// includeOptionalProject("plugins:aqli-plugin-reasoning", "runtime/plugins/aqli-plugin-reasoning")
// includeOptionalProject("plugins:aqli-plugin-sampling", "runtime/plugins/aqli-plugin-sampling")
includeOptionalProject("plugins:aqli-plugin-semantic-cache", "runtime/plugins/aqli-plugin-semantic-cache")
// includeOptionalProject("plugins:aqli-plugin-streaming", "runtime/plugins/aqli-plugin-streaming")
// includeOptionalProject("plugins:aqli-safetensor-rag", "runtime/plugins/aqli-safetensor-rag")
includeOptionalProject("plugins:log-parser", "runtime/plugins/log-parser")
includeOptionalProject("plugins:gamelan", "runtime/plugins/gamelan")



// Optional quantizers
includeOptionalProject("quantizer:aqli-quantizer-autoround", "runtime/quantizer/aqli-quantizer-autoround")
includeOptionalProject("quantizer:aqli-quantizer-awq", "runtime/quantizer/aqli-quantizer-awq")
includeOptionalProject("quantizer:aqli-quantizer-gptq", "runtime/quantizer/aqli-quantizer-gptq")
includeOptionalProject("quantizer:aqli-quantizer-quip", "runtime/quantizer/aqli-quantizer-quip")
includeOptionalProject("quantizer:aqli-quantizer-turboquant", "runtime/quantizer/aqli-quantizer-turboquant")

// Optional runners and runtimes
includeOptionalProject("runner:aqli-diffusion", "runtime/runner/aqli-diffusion")
includeOptionalProject("runtime:aqli-runtime-core", "runtime/core/aqli-runtime-core")
// includeOptionalProject("runtime:distributed:aqli-runtime-distributed", "runtime/core/aqli-runtime-distributed")

// Optional SDK
includeOptionalProject("sdk:aqli-sdk", "framework/sdk/aqli-sdk")
includeOptionalProject("sdk:aqli-sdk-api", "framework/sdk/aqli-sdk-api")
includeOptionalProject("sdk:aqli-sdk-core", "framework/sdk/aqli-sdk-core")
includeOptionalProject("sdk:aqli-sdk-local", "runtime/sdk/aqli-sdk-local")
// includeOptionalProject("sdk:aqli-sdk-remote", "framework/sdk/aqli-sdk-remote")
includeOptionalProject("sdk:aqli-sdk-protobuf", "framework/sdk/aqli-sdk-protobuf")
includeOptionalProject("ui:aqli-sdk-protobuf", "ui/aqli-sdk-protobuf")
if (file("framework/sdk/aqli-sdk-session").isDirectory) {
    include("sdk:aqli-sdk-session")
}

// Optional SPIs
includeOptionalProject("spi:aqli-spi", "framework/spi/aqli-spi")
includeOptionalProject("spi:aqli-spi-audio", "framework/spi/aqli-spi-audio")
includeOptionalProject("spi:aqli-spi-image", "framework/spi/aqli-spi-image")
includeOptionalProject("spi:aqli-spi-inference", "framework/spi/aqli-spi-inference")
includeOptionalProject("spi:aqli-spi-model", "framework/spi/aqli-spi-model")
includeOptionalProject("spi:aqli-spi-multimodal", "framework/spi/aqli-spi-multimodal")
includeOptionalProject("spi:aqli-spi-plugin", "framework/spi/aqli-spi-plugin")
includeOptionalProject("spi:aqli-spi-runtime", "framework/spi/aqli-spi-runtime")

// Optional UI
includeOptionalProject("ui:aqli-api", "ui/aqli-api")
includeOptionalProject("ui:aqli-cli", "ui/aqli-cli")

// Optional backends
includeOptionalProject("backend:blackwell:aqli-kernel-blackwell", "runtime/backend/blackwell/aqli-kernel-blackwell")
includeOptionalProject("backend:blackwell:aqli-plugin-kernel-blackwell", "runtime/backend/blackwell/aqli-plugin-kernel-blackwell")
includeOptionalProject("backend:cpu:aqli-backend-cpu", "runtime/backend/cpu/aqli-backend-cpu")
includeOptionalProject("backend:cuda:aqli-backend-cuda", "runtime/backend/cuda/aqli-backend-cuda")
includeOptionalProject("backend:cuda:aqli-kernel-cuda", "runtime/backend/cuda/aqli-kernel-cuda")
includeOptionalProject("backend:cuda:aqli-plugin-kernel-cuda", "runtime/backend/cuda/aqli-plugin-kernel-cuda")
includeOptionalProject("backend:directml:aqli-plugin-kernel-directml", "runtime/backend/directml/aqli-plugin-kernel-directml")
includeOptionalProject("backend:metal:aqli-backend-metal", "runtime/backend/metal/aqli-backend-metal")
includeOptionalProject("backend:metal:aqli-mlx-binding", "runtime/backend/metal/aqli-mlx-binding")
includeOptionalProject("backend:rocm:aqli-kernel-rocm", "runtime/backend/rocm/aqli-kernel-rocm")
includeOptionalProject("backend:rocm:aqli-plugin-kernel-rocm", "runtime/backend/rocm/aqli-plugin-kernel-rocm")

// Optional core plugins
includeOptionalProject("plugin:aqli-plugin-core", "framework/plugin/aqli-plugin-core")
includeOptionalProject("plugin:aqli-plugin-kernel-core", "framework/plugin/aqli-plugin-kernel-core")
includeOptionalProject("plugin:aqli-plugin-optimization-core", "framework/plugin/aqli-plugin-optimization-core")
includeOptionalProject("plugin:aqli-plugin-runner-core", "framework/plugin/aqli-plugin-runner-core")

// Optional runners
includeOptionalProject("runner:diffuser:aqli-diffuser", "runtime/runner/diffuser/aqli-diffuser")
includeOptionalProject("runner:gguf:aqli-gguf-converter", "runtime/runner/gguf/aqli-gguf-converter")
includeOptionalProject("runner:gguf:aqli-gguf-converter-java", "runtime/runner/gguf/aqli-gguf-converter-java")
includeOptionalProject("runner:gguf:aqli-gguf-core", "runtime/runner/gguf/aqli-gguf-core")
includeOptionalProject("runner:gguf:aqli-runner-gguf", "runtime/runner/gguf/aqli-runner-gguf")
includeOptionalProject("runner:gguf:aqli-plugin-runner-gguf", "runtime/runner/gguf/aqli-plugin-runner-gguf")
includeOptionalProject("runner:gguf:aqli-gguf-feature-text", "runtime/runner/gguf/aqli-gguf-feature-text")
includeOptionalProject("runner:litert:aqli-litert-core", "runtime/runner/litert/aqli-litert-core")
includeOptionalProject("runner:litert:aqli-plugin-runner-litert", "runtime/runner/litert/aqli-plugin-runner-litert")
includeOptionalProject("runner:litert:aqli-runner-litert", "runtime/runner/litert/aqli-runner-litert")

includeOptionalProject("runner:onnx:aqli-plugin-runner-onnx", "runtime/runner/onnx/aqli-plugin-runner-onnx")
includeOptionalProject("runner:onnx:aqli-runner-onnx", "runtime/runner/onnx/aqli-runner-onnx")

includeOptionalProject("runner:safetensor:aqli-safetensor-engine", "runtime/runner/safetensor/aqli-safetensor-engine")
includeOptionalProject("runner:safetensor:aqli-runner-flux", "runtime/runner/safetensor/aqli-runner-flux")
// includeOptionalProject("runner:safetensor:aqli-runner-safetensor", "runtime/runner/safetensor/aqli-runner-safetensor")
// includeOptionalProject("runner:safetensor:aqli-runner-stable-diffusion", "runtime/runner/safetensor/aqli-runner-stable-diffusion")
// includeOptionalProject("runner:safetensor:aqli-safetensor-api", "runtime/runner/safetensor/aqli-safetensor-api")
// includeOptionalProject("runner:safetensor:aqli-safetensor-core", "runtime/runner/safetensor/aqli-safetensor-core")
// includeOptionalProject("runner:safetensor:aqli-safetensor-loader", "runtime/runner/safetensor/aqli-safetensor-loader")
// includeOptionalProject("runner:safetensor:aqli-safetensor-quantization", "runtime/runner/safetensor/aqli-safetensor-quantization")
// includeOptionalProject("runner:safetensor:aqli-safetensor-spi", "runtime/runner/safetensor/aqli-safetensor-spi")
// includeOptionalProject("runner:tensorrt:aqli-runner-tensorrt", "runtime/runner/tensorrt/aqli-runner-tensorrt")
// includeOptionalProject("runner:tensorrt:aqli-plugin-runner-tensorrt", "runtime/runner/tensorrt/aqli-plugin-tensorrt")
// includeOptionalProject("runner:torch:aqli-runner-libtorch", "runtime/runner/torch/aqli-runner-libtorch")





