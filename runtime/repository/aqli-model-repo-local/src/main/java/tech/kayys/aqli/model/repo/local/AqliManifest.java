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
    private String architecture;
    private String repository;
    private boolean pipeline;
    private String branch = "main";
    private String taskType = "text";
    private java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>();
    private String group;
    private boolean enabled = true;

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }
    public String getShortId() { return id != null && id.length() >= 8 ? id.substring(0, 8) : id; }

    public boolean isPipeline() { return pipeline; }
    public void setPipeline(boolean pipeline) { this.pipeline = pipeline; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public java.util.Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(java.util.Map<String, String> metadata) { this.metadata = metadata == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(metadata); }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
