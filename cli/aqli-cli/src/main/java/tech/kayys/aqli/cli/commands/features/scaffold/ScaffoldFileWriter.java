package tech.kayys.aqli.cli.commands.features.scaffold;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs filesystem writes for already-rendered extension scaffold files.
 */
public final class ScaffoldFileWriter {

    private final ScaffoldContentGenerator generator;

    public ScaffoldFileWriter(ScaffoldContentGenerator generator) {
        this.generator = generator;
    }

    public List<Map<String, Object>> write(
            ScaffoldContentGenerator.ScaffoldDefinition scaffold,
            boolean force,
            boolean dryRun) throws IOException {
        Path root = normalize(scaffold.root());
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScaffoldContentGenerator.GeneratedFile file : generator.generate(scaffold)) {
            Path target = normalize(root.resolve(file.relativePath()));
            ensureInside(root, target);
            boolean exists = Files.exists(target);
            String status = dryRun
                    ? exists ? "would-overwrite" : "would-create"
                    : exists ? "overwritten" : "created";
            if (!dryRun) {
                Files.createDirectories(target.getParent());
                if (force) {
                    Files.writeString(target, file.content(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(target, file.content(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW);
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", target.toString());
            item.put("status", status);
            result.add(item);
        }
        return List.copyOf(result);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void ensureInside(Path root, Path target) {
        Path normalizedRoot = normalize(root);
        Path normalizedTarget = normalize(target);
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes extension directory: " + normalizedTarget);
        }
    }
}
