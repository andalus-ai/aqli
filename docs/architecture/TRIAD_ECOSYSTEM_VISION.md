# AQLI + Aljabr + Tafkir: Java AI/ML Ecosystem Vision

**Status:** Strategic target architecture  
**Java baseline:** JDK 25  
**Scope:** The three repositories form one interoperable Java AI/ML ecosystem while remaining independently useful products and libraries.

## 1. Vision

Build a comprehensive, Java-first AI/ML ecosystem that can serve as a practical alternative to the Python AI/ML development ecosystem: not a single monolithic framework, but a coherent set of interoperable projects covering model development, tensor computation, training, inference, deployment, and production serving.

The ecosystem should make Java a first-class environment for AI/ML research, engineering, education, application development, and production operations. It should provide the capabilities developers commonly assemble from tools such as PyTorch, Hugging Face Transformers, scikit-learn, model-format libraries, inference servers, and training toolkits—while respecting Java's strengths: static typing, JVM tooling, Gradle/Maven distribution, concurrency, Java FFM, native interoperability, and production integration.

This is a strategic direction and capability-coverage goal, not a claim that every Python feature or performance level is already implemented. Maintain an explicit capability and compatibility matrix; distinguish implemented, partial, experimental, planned, and unsupported features.

## 2. The triad and ownership

### Aljabr — general-purpose ML and compute framework

Aljabr is the reusable, independently usable ML framework and computational foundation.

It provides the Java-side foundations analogous to the framework and model-development portions of the Python ecosystem, including:

- Tensors, dtypes, shapes, storage, memory, and device abstractions.
- Operators, kernels, graph/IR facilities, compute dispatch, and supported acceleration backends.
- Neural-network primitives, model topology/configuration, reusable model execution, and autograd/optimization capabilities as implemented.
- Model formats and weight access, including GGUF and Safetensors, plus optional ONNX/LiteRT integrations.
- Tokenizer contracts and reusable tokenizer implementations.
- Java FFM/native interoperability and low-level compute APIs.
- Reusable data/ML utilities where they belong in Aljabr's framework scope.

Aljabr must work as a library without an AQLI server or Tafkir training runtime. It must not depend on AQLI or own serving/deployment policy.

### Tafkir — training, fine-tuning, and adaptation

Tafkir provides training-oriented workflows on top of Aljabr.

It owns:

- Training loops and orchestration.
- Fine-tuning, adaptation, and evaluation workflows.
- Training lifecycle, datasets/batches, checkpoint/training-run management, and optimizer orchestration where implemented.
- Optional notebook/Jupyter, JBang, and scripted training experiences.
- Export and handoff of model artifacts that can be consumed by Aljabr and AQLI.

Tafkir reuses Aljabr's tensor, model, autograd, optimizer, backend, and artifact foundations rather than creating a competing compute stack. Tafkir is not an AQLI training mode and is not a mandatory dependency of ordinary AQLI inference.

### AQLI — inference, runtime, deployment, and serving

AQLI turns model capabilities into usable embedded and production inference.

It owns:

- Model lifecycle, repository/download integration, deployment configuration, and runtime packaging.
- Request contracts at the serving boundary, admission, quotas, scheduling, batching, routing, and runtime engine selection.
- Inference orchestration, generation/sampling policy, sessions, streaming, cancellation, deadlines, and resource lifecycle.
- CLI, Java SDK, scripting integrations, REST/HTTP and gRPC service interfaces.
- Production operations: observability, authentication integration, tenant isolation, health/admin operations, graceful shutdown, and reliability controls.

AQLI consumes Aljabr's public framework APIs. It may integrate with Tafkir through optional artifact/workflow integrations, but ordinary inference must not require Tafkir.

## 3. Relationship to the Python ecosystem

The goal is capability coverage and an integrated developer experience, not a one-to-one package-name clone.

| Python ecosystem role | Java triad responsibility |
| --- | --- |
| PyTorch-like tensor, neural-network, autograd, and compute foundation | Aljabr |
| Transformers/model architectures, tokenizers, model formats, reusable model execution | Primarily Aljabr; serving/model lifecycle integration in AQLI |
| scikit-learn-like reusable ML utilities and workflows | Aljabr where framework-generic; Tafkir for training/evaluation workflows; capability coverage must be documented |
| Training/fine-tuning toolkits | Tafkir on Aljabr |
| Inference runtimes and model-serving systems | AQLI on Aljabr |
| Model distribution and artifact lifecycle | AQLI repository/lifecycle integration, with portable artifacts and formats shared through Aljabr |
| Java application and enterprise integration | All three through stable Java APIs; AQLI additionally provides production serving |

Do not claim full scikit-learn parity merely because Aljabr is a general ML framework. Classical ML algorithms, preprocessing, feature engineering, metrics, datasets, and model-selection utilities need an explicit inventory and roadmap. Likewise, Transformers parity requires model architecture, tokenizer, processor, weight mapping, multimodal, and task coverage—not just model file loading.

## 4. One ecosystem, multiple ways to use it

The triad must be consumable at different levels without forcing every user to deploy the whole stack.

1. **Embedded Java library:** use Aljabr directly for tensor, model, and compute workloads; use AQLI's local SDK when inference lifecycle and serving abstractions are useful.
2. **Java application SDK:** call typed APIs from ordinary Java applications and services without launching a CLI process.
3. **CLI:** use AQLI for model acquisition, inference, inspection, and operational workflows; use Tafkir/Aljabr command-line tooling for training and framework workflows where provided.
4. **Scripts:** support JBang and straightforward Java entry points for repeatable experiments, automation, batch jobs, and CI tasks.
5. **Notebook and interactive development:** support JShell and Jupyter/JVM notebook workflows where maintained; notebooks are an interface over the same Java APIs, not a separate implementation.
6. **Production runtime:** deploy AQLI as an embedded runtime, standalone service, or packaged production process with REST/gRPC and operational controls.
7. **Training-to-serving workflow:** train or adapt with Tafkir, export portable artifacts, validate/load through Aljabr, then deploy and serve through AQLI.

The same typed contracts and model artifacts should be reusable across these modes. A CLI-only feature is not a complete capability if it is intended to be part of the public ML ecosystem.

## 5. Contract and dependency principles

- Aljabr owns reusable, engine-neutral ML/compute contracts and implementations.
- Tafkir owns training workflow semantics and builds on Aljabr.
- AQLI owns inference-serving and production runtime semantics and builds on Aljabr.
- Aljabr must not depend on AQLI or Tafkir. Tafkir must not require AQLI internals. AQLI must not require Tafkir for normal inference.
- Public APIs should be typed, documented, versioned, and independently consumable.
- CLI, REST, gRPC, notebooks, scripts, and embedded Java use cases are adapters or entry points over shared APIs—not separate implementations of model behavior.
- Capability-specific contracts belong to the owning capability; do not accumulate audio/image/video/spatial/training fields in a universal request class.
- Registries discover pluggable capabilities and implementations; they do not replace canonical contracts or own execution semantics.
- Runtime backend selection belongs to the relevant runtime. Aljabr exposes backend SPIs and capabilities.
- Prefer Java FFM and Java-native implementations where mature; optional native/provider integrations must remain isolated and accurately documented.
- Keep optional integrations optional and avoid mandatory dependencies between the triad's products.

## 6. Developer experience and distribution

Treat developer experience as a core product requirement:

- Consistent Gradle/Maven artifacts, dependency/version alignment, and published API documentation.
- Quick starts for embedded Java, SDK, CLI, JBang, scripts, notebooks, and production service deployment.
- End-to-end tutorials: tensor basics, model loading, inference, fine-tuning, evaluation, export, and serving.
- Runnable examples that use published artifacts rather than relying only on a monorepo/composite build.
- Clear capability matrices by task, model family, format, backend, OS/architecture, and maturity status.
- Stable extension points for custom operators, backends, model architectures, tokenizers, tasks, and runtime providers.
- Reproducible benchmarks and correctness evidence, not unqualified performance claims.

## 7. Capability roadmap and honest parity

Track capability coverage across the three projects, rather than treating the Python ecosystem as one feature.

Suggested capability families include:

- Core computation: tensors, broadcasting, linear algebra, autograd, optimizers, neural-network layers, graph execution, CPU/vector/GPU backends.
- Model ecosystem: architectures, weight loading/mapping, tokenizers, processors, multimodal inputs, model formats, quantization.
- Classical ML: preprocessing, feature transforms, estimators, clustering, classification, regression, dimensionality reduction, metrics, model selection, pipelines.
- Training: datasets, batching, training loops, fine-tuning, evaluation, checkpoints, distributed/accelerated training where supported.
- Inference: text, embeddings, reranking, audio, image, video, spatial/3D, multimodal tasks, streaming and batch inference.
- Production: model registry, deployment, APIs, scheduling, concurrency, observability, security, lifecycle, and reliability.
- Developer workflows: Java SDK, CLI, JBang, scripts, JShell, notebooks, documentation, examples, and packaging.

For every item record owner, API/module, supported formats/backends, maturity, tests, examples, and known limitations. Do not use “PyTorch alternative”, “Transformers alternative”, or “scikit-learn alternative” as a blanket implementation claim without a published feature matrix.

## 8. Architecture acceptance criteria

The vision is progressing when:

- Aljabr can be used independently as a Java ML/compute framework.
- Tafkir can train/adapt/evaluate using Aljabr primitives and export artifacts without depending on AQLI internals.
- AQLI can embed and serve supported Aljabr models through SDK, CLI, script, REST, and gRPC entry points.
- The same capability semantics are available through relevant transports and programmatic APIs.
- Training artifacts can flow into inference without format-specific duplication or mandatory Tafkir runtime dependencies.
- Users can follow maintained examples from development to training to production deployment.
- Capability and performance claims are backed by tests, compatibility matrices, and reproducible benchmarks.
