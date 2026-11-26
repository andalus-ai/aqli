package tech.kayys.aqli.cli.commands.features.packaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads an AQLI extension distribution package and exposes its declared Java artifact.
 *
 * <p>This is package I/O only: it does not load providers, register capabilities, or
 * select runtime engines. Capability semantics remain owned by their respective APIs
 * and implementations.</p>
 */
public final class ExtensionPackageReader {
    private static final String PACKAGE_MANIFEST_ENTRY = "aqli-package.json";

    private ExtensionPackageReader() {
    }

    public static InstallSource resolve(Path source) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        String filename = normalized.getFileName() == null ? "" : normalized.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".jar")) {
            return new InstallSource(normalized, normalized, null, null);
        }
        if (!filename.endsWith(".zip")) {
            throw new IllegalArgumentException("Extension artifact must be a .jar or .zip package: " + source);
        }
        return resolvePackage(normalized);
    }

    public static Map<String, Object> packageJarArtifact(Map<String, Object> packageManifest) {
        Object raw = packageManifest == null ? null : packageManifest.get("artifacts");
        if (!(raw instanceof List<?> artifacts)) {
            return Map.of();
        }
        for (Object item : artifacts) {
            if (!(item instanceof Map<?, ?> rawArtifact)) {
                continue;
            }
            Map<String, Object> artifact = new LinkedHashMap<>();
            rawArtifact.forEach((key, value) -> {
                if (key != null) {
                    artifact.put(String.valueOf(key), value);
                }
            });
            String type = value(artifact, "type");
            String artifactPath = value(artifact, "path").replace('\\', '/');
            if (!artifactPath.isBlank() && artifactPath.endsWith(".jar")
                    && (type.isBlank() || "java-jar".equals(type) || "jar".equals(type))) {
                return Collections.unmodifiableMap(new LinkedHashMap<>(artifact));
            }
        }
        return Map.of();
    }

    private static InstallSource resolvePackage(Path packagePath) throws IOException {
        Path tempDir = null;
        try (ZipFile zip = new ZipFile(packagePath.toFile())) {
            ZipEntry manifestEntry = findPackageManifestEntry(zip);
            Map<String, Object> manifest = readJsonObject(zip, manifestEntry);
            String root = zipEntryRoot(manifestEntry.getName());
            String artifactPath = value(packageJarArtifact(manifest), "path").replace('\\', '/');
            if (artifactPath.isBlank()) {
                throw new IllegalArgumentException("Extension package has no java-jar artifact in " + PACKAGE_MANIFEST_ENTRY);
            }
            String artifactEntryName = safeZipEntryName(root, artifactPath);
            ZipEntry artifactEntry = zip.getEntry(artifactEntryName);
            if (artifactEntry == null || artifactEntry.isDirectory()) {
                throw new IllegalArgumentException("Extension package jar artifact not found: " + artifactEntryName);
            }
            tempDir = Files.createTempDirectory("aqli-extension-package.");
            Path artifactName = Path.of(artifactPath).getFileName();
            if (artifactName == null) {
                throw new IllegalArgumentException("Invalid extension package artifact path: " + artifactPath);
            }
            Path tempJar = tempDir.resolve(artifactName.toString()).normalize();
            if (!tempJar.startsWith(tempDir)) {
                throw new IllegalArgumentException("Invalid extension package artifact path: " + artifactPath);
            }
            try (var input = zip.getInputStream(artifactEntry)) {
                Files.copy(input, tempJar, StandardCopyOption.REPLACE_EXISTING);
            }
            PackageReport report = new PackageReport(
                    packagePath.toString(),
                    manifest,
                    manifestEntry.getName(),
                    artifactEntryName,
                    artifactName.toString(),
                    Files.size(packagePath),
                    sha256(packagePath),
                    Files.size(tempJar),
                    sha256(tempJar));
            return new InstallSource(packagePath, tempJar, report, tempDir);
        } catch (Exception e) {
            deleteRecursively(tempDir);
            if (e instanceof IOException io) {
                throw io;
            }
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static ZipEntry findPackageManifestEntry(ZipFile zip) {
        List<? extends ZipEntry> entries = zip.stream()
                .filter(entry -> entry != null && !entry.isDirectory())
                .filter(entry -> {
                    String name = entry.getName();
                    return PACKAGE_MANIFEST_ENTRY.equals(name) || name.endsWith("/" + PACKAGE_MANIFEST_ENTRY);
                })
                .toList();
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Extension package is missing " + PACKAGE_MANIFEST_ENTRY);
        }
        if (entries.size() > 1) {
            throw new IllegalArgumentException("Extension package contains multiple " + PACKAGE_MANIFEST_ENTRY + " files");
        }
        return entries.get(0);
    }

    private static Map<String, Object> readJsonObject(ZipFile zip, ZipEntry entry) throws IOException {
        try (var input = zip.getInputStream(entry)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = new ObjectMapper().readValue(input, Map.class);
            return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
        }
    }

    private static String zipEntryRoot(String entryName) {
        int slash = entryName == null ? -1 : entryName.lastIndexOf('/');
        return slash < 0 ? "" : entryName.substring(0, slash + 1);
    }

    private static String safeZipEntryName(String root, String relative) {
        String cleanRoot = root == null ? "" : root;
        String cleanRelative = relative == null ? "" : relative.replace('\\', '/');
        if (cleanRelative.isBlank() || cleanRelative.startsWith("/") || cleanRelative.contains("\0")) {
            throw new IllegalArgumentException("Invalid extension package artifact path: " + relative);
        }
        for (String part : cleanRelative.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Invalid extension package artifact path: " + relative);
            }
        }
        return cleanRoot + cleanRelative;
    }

    private static String sha256(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is unavailable", e);
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String value(Map<String, Object> map, String key) {
        Object raw = map == null ? null : map.get(key);
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    public record PackageReport(
            String path,
            Map<String, Object> manifest,
            String manifestEntryName,
            String artifactEntryName,
            String installFilename,
            long sizeBytes,
            String sha256,
            long artifactSizeBytes,
            String artifactSha256) {
        public PackageReport {
            manifest = manifest == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(manifest));
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("manifest_entry", manifestEntryName);
            result.put("artifact_entry", artifactEntryName);
            result.put("install_filename", installFilename);
            if (artifactSizeBytes >= 0) result.put("artifact_size_bytes", artifactSizeBytes);
            if (artifactSha256 != null && !artifactSha256.isBlank()) result.put("artifact_sha256", artifactSha256);
            if (sizeBytes >= 0) result.put("size_bytes", sizeBytes);
            if (sha256 != null && !sha256.isBlank()) result.put("sha256", sha256);
            result.put("manifest", manifest);
            return result;
        }

        public Map<String, Object> toLockMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("manifest_entry", manifestEntryName);
            result.put("artifact_entry", artifactEntryName);
            result.put("install_filename", installFilename);
            if (artifactSizeBytes >= 0) result.put("artifact_size_bytes", artifactSizeBytes);
            if (artifactSha256 != null && !artifactSha256.isBlank()) result.put("artifact_sha256", artifactSha256);
            if (sizeBytes >= 0) result.put("size_bytes", sizeBytes);
            if (sha256 != null && !sha256.isBlank()) result.put("sha256", sha256);
            result.put("id", value(manifest, "id"));
            result.put("name", value(manifest, "name"));
            result.put("version", value(manifest, "version"));
            result.put("kind", value(manifest, "kind"));
            Object requires = manifest.get("requires");
            if (requires instanceof Map<?, ?> rawRequires) {
                Map<String, Object> copied = new LinkedHashMap<>();
                rawRequires.forEach((key, item) -> {
                    if (key != null) copied.put(String.valueOf(key), item);
                });
                result.put("requires", copied);
            }
            return result;
        }
    }

    public static final class InstallSource implements AutoCloseable {
        private final Path sourcePath;
        private final Path jarPath;
        private final PackageReport packageReport;
        private final Path tempDir;

        private InstallSource(Path sourcePath, Path jarPath, PackageReport packageReport, Path tempDir) {
            this.sourcePath = sourcePath;
            this.jarPath = jarPath;
            this.packageReport = packageReport;
            this.tempDir = tempDir;
        }

        public Path jarPath() { return jarPath; }
        public PackageReport packageReport() { return packageReport; }

        public String defaultInstalledName() {
            if (packageReport != null && packageReport.installFilename() != null && !packageReport.installFilename().isBlank()) {
                return packageReport.installFilename();
            }
            return sourcePath.getFileName() == null ? sourcePath.toString() : sourcePath.getFileName().toString();
        }

        public String displayJarPath() {
            return packageReport == null ? jarPath.toString() : sourcePath + "!" + packageReport.artifactEntryName();
        }

        @Override
        public void close() throws IOException {
            deleteRecursively(tempDir);
        }
    }
}
