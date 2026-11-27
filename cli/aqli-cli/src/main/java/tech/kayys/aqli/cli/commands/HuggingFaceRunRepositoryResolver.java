package tech.kayys.aqli.cli.commands;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import tech.kayys.aqli.sdk.model.HuggingFacePullRequest;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;

/**
 * Applies the run command's policy for resolving Hugging Face references locally.
 *
 * <p>It owns the cache/offline/refresh decision and pull lifecycle. Progress
 * presentation and local-index storage remain supplied by the CLI adapter.</p>
 */
final class HuggingFaceRunRepositoryResolver {

    record Options(
            String requestedModel,
            String preferredFormat,
            String providerId,
            boolean mcpProvider,
            boolean offline,
            boolean force,
            String branch,
            boolean quietOutput,
            boolean suppressPullNotice) {
    }

    private HuggingFaceRunRepositoryResolver() {
    }

    static LocalModelIndex.Entry resolve(
            Options options,
            AqliRuntime runtime,
            BiFunction<String, String, Optional<LocalModelIndex.Entry>> localIndexLookup,
            Runnable refreshLocalIndex,
            Consumer<String> standardOutput,
            Consumer<String> standardError,
            Consumer<PullProgress> progressCallback) throws Exception {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(localIndexLookup, "localIndexLookup");
        Objects.requireNonNull(refreshLocalIndex, "refreshLocalIndex");
        Objects.requireNonNull(standardOutput, "standardOutput");
        Objects.requireNonNull(standardError, "standardError");
        Objects.requireNonNull(progressCallback, "progressCallback");

        String repositoryId = ModelSourceResolver.huggingFaceRepositoryId(options.requestedModel());
        if (repositoryId == null
                || ModelSourceRoutingPolicy.isRemoteProvider(options.providerId())
                || options.mcpProvider()) {
            return null;
        }

        Optional<LocalModelIndex.Entry> existing = localIndexLookup.apply(repositoryId, options.preferredFormat());
        if (existing.isPresent() && !options.force()) {
            return existing.get();
        }

        if (options.offline()) {
            standardError.accept("Error: Model " + repositoryId
                    + " is not installed locally, and --offline/--local disables automatic downloads.");
            standardError.accept("Run once online: aqli pull " + repositoryId);
            return null;
        }

        String branch = options.branch();
        String revision = branch == null || branch.isBlank() ? "main" : branch.trim();
        if (!options.suppressPullNotice()) {
            standardOutput.accept("Model not found locally; pulling hf:" + repositoryId
                    + ("main".equalsIgnoreCase(revision) ? "" : "@" + revision) + "...");
        }

        runtime.pullHuggingFaceModel(
                HuggingFacePullRequest.builder()
                        .modelSpec("hf:" + repositoryId)
                        .revision(revision)
                        .format(options.preferredFormat())
                        .force(options.force())
                        .build(),
                progressCallback);

        if (!options.quietOutput()) {
            standardOutput.accept("");
        }
        refreshLocalIndex.run();

        Optional<LocalModelIndex.Entry> downloaded = localIndexLookup.apply(repositoryId, null);
        if (downloaded.isPresent()) {
            return downloaded.get();
        }
        throw new IllegalStateException("download finished, but no local"
                + (options.preferredFormat() == null ? "" : " " + options.preferredFormat())
                + " artifact was indexed for " + repositoryId);
    }
}
