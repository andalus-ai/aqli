package tech.kayys.aqli.model.repo.local;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted metadata for a locally cached model artifact.
 *
 * <p>This is an Aqli-owned cache record; it is deliberately independent of
 * training/quantization modules.</p>
 */
public class AqliManifest {
    private String id;
    private String modelId;
    private String name;
    private String format;
    private String source;
    private String revision;
    private String blobPath;
    private List<String> files = new ArrayList<>();
    private Instant createdAt;
    private long sizeBytes;

    public static String computeName(String modelId, String revision) {
        String model = modelId == null ? "" : modelId.trim();
        String rev = revision == null || revision.isBlank() ? "main" : revision.trim();
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((model + "\n" + rev).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", bytes[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public String getBlobPath() { return blobPath; }
    public void setBlobPath(String blobPath) { this.blobPath = blobPath; }
    public List<String> getFiles() { return files; }
    public void setFiles(List<String> files) { this.files = files == null ? new ArrayList<>() : new ArrayList<>(files); }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
}
