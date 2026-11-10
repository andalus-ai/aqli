package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.plugin.core.ExtensionAvailability;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small release-gate policy for detachable extensions.
 */
public record ExtensionAvailabilityPolicy(
        List<String> requiredExtensions,
        List<String> requiredProductionExtensions,
        List<String> forbiddenExtensions) {
    public static final String REQUIRED_PROPERTY = "aqli.requiredExtensions";
    public static final String REQUIRED_PRODUCTION_PROPERTY = "aqli.requiredProductionExtensions";
    public static final String FORBIDDEN_PROPERTY = "aqli.forbiddenExtensions";
    public static final String REQUIRED_ENV = "AQLI_REQUIRED_EXTENSIONS";
    public static final String REQUIRED_PRODUCTION_ENV = "AQLI_REQUIRED_PRODUCTION_EXTENSIONS";
    public static final String FORBIDDEN_ENV = "AQLI_FORBIDDEN_EXTENSIONS";

    public ExtensionAvailabilityPolicy {
        requiredExtensions = normalize(requiredExtensions);
        requiredProductionExtensions = normalize(requiredProductionExtensions);
        forbiddenExtensions = normalize(forbiddenExtensions);
    }

    public static ExtensionAvailabilityPolicy empty() {
        return new ExtensionAvailabilityPolicy(List.of(), List.of(), List.of());
    }

    public static ExtensionAvailabilityPolicy fromRuntimeConfiguration() {
        return new ExtensionAvailabilityPolicy(
                configuredList(REQUIRED_PROPERTY, REQUIRED_ENV),
                configuredList(REQUIRED_PRODUCTION_PROPERTY, REQUIRED_PRODUCTION_ENV),
                configuredList(FORBIDDEN_PROPERTY, FORBIDDEN_ENV));
    }

    public boolean configured() {
        return !requiredExtensions.isEmpty()
                || !requiredProductionExtensions.isEmpty()
                || !forbiddenExtensions.isEmpty();
    }

    public Result evaluate(List<ExtensionAvailability> availabilityReports) {
        Map<String, ExtensionAvailability> byId = new LinkedHashMap<>();
        if (availabilityReports != null) {
            for (ExtensionAvailability availability : availabilityReports) {
                byId.putIfAbsent(availability.id(), availability);
            }
        }

        Set<String> violations = new LinkedHashSet<>();
        for (String extensionId : requiredExtensions) {
            ExtensionAvailability availability = byId.get(extensionId);
            if (availability == null) {
                violations.add("required extension " + extensionId + " was not discovered");
            } else if (!availability.attached()) {
                violations.add("required extension " + extensionId + " is " + availability.status());
            } else if (!availability.healthy()) {
                violations.add("required extension " + extensionId + " is unhealthy");
            }
        }
        for (String extensionId : requiredProductionExtensions) {
            ExtensionAvailability availability = byId.get(extensionId);
            if (availability == null) {
                violations.add("production extension " + extensionId + " was not discovered");
            } else if (!availability.productionReady()) {
                violations.add("production extension " + extensionId + " is not production-ready: "
                        + availability.status());
            } else if (!availability.healthy()) {
                violations.add("production extension " + extensionId + " is unhealthy");
            }
        }
        for (String extensionId : forbiddenExtensions) {
            ExtensionAvailability availability = byId.get(extensionId);
            if (availability != null && availability.attached()) {
                violations.add("forbidden extension " + extensionId + " is attached");
            }
        }

        boolean configured = configured();
        boolean passed = violations.isEmpty();
        String status = !configured ? "not_configured" : passed ? "passed" : "failed";
        return new Result(
                configured,
                passed,
                status,
                List.copyOf(violations),
                requiredExtensions,
                requiredProductionExtensions,
                forbiddenExtensions);
    }

    private static List<String> configuredList(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentName);
        }
        return parseList(value);
    }

    private static List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return normalize(List.of(value.split(",")));
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return List.copyOf(normalized);
    }

    public record Result(
            boolean configured,
            boolean passed,
            String status,
            List<String> violations,
            List<String> requiredExtensions,
            List<String> requiredProductionExtensions,
            List<String> forbiddenExtensions) {
        public Result {
            status = status == null || status.isBlank() ? "unknown" : status;
            violations = List.copyOf(violations == null ? List.of() : violations);
            requiredExtensions = List.copyOf(requiredExtensions == null ? List.of() : requiredExtensions);
            requiredProductionExtensions =
                    List.copyOf(requiredProductionExtensions == null ? List.of() : requiredProductionExtensions);
            forbiddenExtensions = List.copyOf(forbiddenExtensions == null ? List.of() : forbiddenExtensions);
        }
    }
}
