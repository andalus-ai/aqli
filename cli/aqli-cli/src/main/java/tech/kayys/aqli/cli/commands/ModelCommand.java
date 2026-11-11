package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.kayys.aqli.model.repo.local.AqliManifest;
import tech.kayys.aqli.model.repo.local.ManifestStore;
import tech.kayys.aqli.sdk.util.AqliHome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Dependent
@Unremovable
@Command(name = "model", description = "Manage local models (pull, rename, remove, enable, export, publish)",
         subcommands = {
             PullCommand.class,
             DeleteCommand.class,
             ModelCommand.RenameCommand.class,
             ModelCommand.EnableCommand.class,
             ModelCommand.ExportCommand.class,
             ModelCommand.PublishCommand.class
         })
public class ModelCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Dependent
    @Unremovable
    @Command(name = "rename", description = "Rename a local model metadata")
    public static class RenameCommand implements Runnable {
        @Inject
        ManifestStore manifestStore;

        @Parameters(index = "0", description = "Model ID or short ID to rename")
        String modelRef;

        @Parameters(index = "1", description = "New identity in format <GROUP>:<NAME>:<ARCH> (e.g. ibm-granite:granite-4.1-3b:granite)")
        String newIdentity;

        @Override
        public void run() {
            String[] parts = newIdentity.split(":", -1);
            if (parts.length > 3) {
                System.err.println("\u001B[31mInvalid new identity format. Must be at most <GROUP>:<NAME>:<ARCH>\u001B[0m");
                return;
            }

            Optional<AqliManifest> manifestOpt = findManifest(manifestStore, modelRef);
            if (manifestOpt.isEmpty()) {
                System.err.println("\u001B[31mModel not found: " + modelRef + "\u001B[0m");
                return;
            }

            AqliManifest manifest = manifestOpt.get();
            if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                manifest.setGroup(parts[0].trim());
            }
            if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                manifest.setName(parts[1].trim());
            }
            if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                manifest.setArchitecture(parts[2].trim());
            }
            try {
                manifestStore.save(manifest);
            } catch (Exception e) {
                System.err.println("\u001B[31mFailed to save manifest: " + e.getMessage() + "\u001B[0m");
                return;
            }

            LocalModelIndex.refreshFromDisk();
            System.out.println("\u001B[32mSuccessfully renamed model '" + modelRef + "' to " 
                + manifest.getGroup() + ":" + manifest.getName() + " (" + manifest.getArchitecture() + ")\u001B[0m");
        }
    }

    @Dependent
    @Unremovable
    @Command(name = "enable", description = "Enable or disable a model for inference")
    public static class EnableCommand implements Runnable {
        @Inject
        ManifestStore manifestStore;

        @Parameters(index = "0", description = "Model ID or short ID")
        String modelRef;

        @Parameters(index = "1", description = "true or false")
        boolean enabled;

        @Override
        public void run() {
            Optional<AqliManifest> manifestOpt = findManifest(manifestStore, modelRef);
            if (manifestOpt.isEmpty()) {
                System.err.println("\u001B[31mModel not found: " + modelRef + "\u001B[0m");
                return;
            }

            AqliManifest manifest = manifestOpt.get();
            manifest.setEnabled(enabled);
            try {
                manifestStore.save(manifest);
            } catch (Exception e) {
                System.err.println("\u001B[31mFailed to save manifest: " + e.getMessage() + "\u001B[0m");
                return;
            }

            LocalModelIndex.refreshFromDisk();
            System.out.println("\u001B[32mModel '" + manifest.getShortId() + "' (" + manifest.getName() + ") has been " + (enabled ? "enabled" : "disabled") + ".\u001B[0m");
        }
    }

    @Dependent
    @Unremovable
    @Command(name = "export", description = "Export a local model's blobs to a specified path")
    public static class ExportCommand implements Runnable {
        @Inject
        ManifestStore manifestStore;

        @Parameters(index = "0", description = "Model ID or short ID to export")
        String modelRef;

        @Option(names = { "-o", "--output" }, required = true, description = "Output directory or file path")
        String outputPath;

        @Override
        public void run() {
            Optional<AqliManifest> manifestOpt = findManifest(manifestStore, modelRef);
            if (manifestOpt.isEmpty()) {
                System.err.println("\u001B[31mModel not found: " + modelRef + "\u001B[0m");
                return;
            }

            AqliManifest manifest = manifestOpt.get();
            if (manifest.getBlobPath() == null || manifest.getBlobPath().isBlank()) {
                System.err.println("\u001B[31mManifest does not contain a blob path.\u001B[0m");
                return;
            }

            Path sourcePath = Path.of(manifest.getBlobPath());
            Path targetPath = Path.of(outputPath).toAbsolutePath();

            if (!Files.exists(sourcePath)) {
                System.err.println("\u001B[31mSource blob path does not exist: " + sourcePath + "\u001B[0m");
                return;
            }

            try {
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                    System.out.println("Exporting directory " + sourcePath + " to " + targetPath + " ...");
                    final Path finalTargetPath = targetPath;
                    try (var stream = Files.walk(sourcePath)) {
                        stream.forEach(source -> {
                            Path destination = finalTargetPath.resolve(sourcePath.relativize(source));
                            try {
                                if (Files.isDirectory(source)) {
                                    Files.createDirectories(destination);
                                } else {
                                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (Exception e) {
                                System.err.println("Failed to copy " + source + ": " + e.getMessage());
                            }
                        });
                    }
                } else {
                    if (Files.isDirectory(targetPath)) {
                        targetPath = targetPath.resolve(sourcePath.getFileName());
                    } else {
                        Files.createDirectories(targetPath.getParent());
                    }
                    System.out.println("Exporting file " + sourcePath + " to " + targetPath + " ...");
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("\u001B[32mExport complete: " + targetPath + "\u001B[0m");
            } catch (Exception e) {
                System.err.println("\u001B[31mExport failed: " + e.getMessage() + "\u001B[0m");
            }
        }
    }

    @Dependent
    @Unremovable
    @Command(name = "publish", description = "Publish a local model to Hugging Face (scaffolding)")
    public static class PublishCommand implements Runnable {
        @Parameters(index = "0", description = "Model ID or short ID to publish")
        String modelRef;

        @Option(names = { "-hf", "--huggingface" }, required = true, description = "HuggingFace target repository (e.g. user/my-model)")
        String hfRepo;

        @Override
        public void run() {
            System.out.println("\u001B[33mPublishing to HuggingFace is currently a stub.\u001B[0m");
            System.out.println("Target Model Ref: " + modelRef);
            System.out.println("Target HF Repo: " + hfRepo);
            System.out.println("To be implemented: authentication, chunking, and HTTP multipart upload to HF Hub.");
        }
    }

    private static Optional<AqliManifest> findManifest(ManifestStore store, String ref) {
        Optional<AqliManifest> opt = store.findByShortId(ref);
        if (opt.isPresent()) return opt;
        opt = store.findById(ref);
        if (opt.isPresent()) return opt;
        return store.findByName(ref);
    }
}
