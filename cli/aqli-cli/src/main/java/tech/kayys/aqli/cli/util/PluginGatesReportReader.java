/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.PluginGatesReportFields.Gate;
import tech.kayys.aqli.cli.util.PluginGatesReportFields.Root;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rehydrates plugin-gate results from CI report maps while preserving older report compatibility.
 */
final class PluginGatesReportReader {
    private PluginGatesReportReader() {
    }

    static PluginGates gatesFromReport(Map<String, Object> report) {
        if (report == null) {
            return PluginGates.evaluate(null, null);
        }
        Map<String, Object> modelFamilyBundle = asMap(report.get(Root.MODEL_FAMILY_BUNDLE));
        Map<String, Object> runnerRouteContracts = asMap(report.get(Root.RUNNER_ROUTE_CONTRACTS));
        Map<String, Object> routeBenchmarkCacheReportContract =
                asMap(report.get(Root.ROUTE_BENCHMARK_CACHE_REPORT_CONTRACT));
        Map<String, Object> topLevelGate = asMap(report.get(Root.GATE));
        if (topLevelGate != null) {
            return withRouteBenchmarkCacheReportContract(
                    withRunnerRouteContractBundle(
                            withModelFamilyContractReportContract(
                                    pluginGateFromReport(topLevelGate),
                                    modelFamilyBundle),
                            runnerRouteContracts),
                    routeBenchmarkCacheReportContract);
        }
        return withRouteBenchmarkCacheReportContract(
                withRunnerRouteContractBundle(
                        gatesFromReports(
                                asMap(report.get(Root.EXTENSION_AVAILABILITY)),
                                modelFamilyBundle),
                        runnerRouteContracts),
                routeBenchmarkCacheReportContract);
    }

    static PluginGates gatesFromReports(
            Map<String, Object> extensionAvailability,
            Map<String, Object> modelFamilyBundle) {
        return withModelFamilyContractReportContract(
                PluginGates.evaluate(
                        extensionGateFromReport(asMap(
                                extensionAvailability == null
                                        ? null
                                        : extensionAvailability.get(
                                                ExtensionAvailabilityGateReportFields.Root.GATE))),
                        modelFamilyGateFromReport(asMap(
                                modelFamilyBundle == null
                                        ? null
                                        : modelFamilyBundle.get(ModelFamilyBundleGateReportFields.Root.GATE)))),
                modelFamilyBundle);
    }

    private static PluginGates withModelFamilyContractReportContract(
            PluginGates gates,
            Map<String, Object> modelFamilyBundle) {
        if (modelFamilyBundle == null) {
            return gates;
        }
        return PluginGates.withModelFamilyContractReportContract(
                gates,
                asMap(modelFamilyBundle.get(ModelFamilyBundleGateReportFields.Root.CONTRACT)));
    }

    private static PluginGates withRunnerRouteContractBundle(
            PluginGates gates,
            Map<String, Object> runnerRouteContracts) {
        return RunnerRouteContractBundleReports.applyGateFromSection(gates, runnerRouteContracts);
    }

    private static PluginGates withRouteBenchmarkCacheReportContract(
            PluginGates gates,
            Map<String, Object> routeBenchmarkCacheReportContract) {
        return RouteBenchmarkCacheReportContract.applySchemaGateFromSection(
                gates,
                routeBenchmarkCacheReportContract);
    }

    private static PluginGates pluginGateFromReport(Map<String, Object> gate) {
        if (gate == null) {
            return PluginGates.evaluate(null, null);
        }
        return new PluginGates(
                Boolean.TRUE.equals(gate.get(Gate.PASSED)),
                stringValue(gate.get(Gate.STATUS), "unknown"),
                intValue(gate.get(Gate.VIOLATION_COUNT)),
                stringList(gate.get(Gate.VIOLATIONS)),
                stringValue(gate.get(Gate.EXTENSION_STATUS), "unknown"),
                stringValue(gate.get(Gate.MODEL_FAMILY_STATUS), "unknown"),
                intValue(gate.get(Gate.EXTENSION_VIOLATION_COUNT)),
                intValue(gate.get(Gate.MODEL_FAMILY_VIOLATION_COUNT)),
                intMap(gate.get(Gate.MODEL_FAMILY_CONTRACT_CATEGORY_COUNTS)),
                stringList(gate.get(Gate.MODEL_FAMILY_CONTRACT_REMEDIATION_HINTS)));
    }

    private static ExtensionAvailabilityGate extensionGateFromReport(Map<String, Object> gate) {
        if (gate == null) {
            return null;
        }
        return new ExtensionAvailabilityGate(
                Boolean.TRUE.equals(gate.get(ExtensionAvailabilityGateReportFields.Gate.PASSED)),
                stringValue(gate.get(ExtensionAvailabilityGateReportFields.Gate.STATUS), "unknown"),
                intValue(gate.get(ExtensionAvailabilityGateReportFields.Gate.VIOLATION_COUNT)),
                stringList(gate.get(ExtensionAvailabilityGateReportFields.Gate.VIOLATIONS)));
    }

    private static ModelFamilyBundleGate modelFamilyGateFromReport(Map<String, Object> gate) {
        if (gate == null) {
            return null;
        }
        return new ModelFamilyBundleGate(
                Boolean.TRUE.equals(gate.get(ModelFamilyBundleGateReportFields.Gate.PASSED)),
                stringValue(gate.get(ModelFamilyBundleGateReportFields.Gate.STATUS), "unknown"),
                intValue(gate.get(ModelFamilyBundleGateReportFields.Gate.VIOLATION_COUNT)),
                stringList(gate.get(ModelFamilyBundleGateReportFields.Gate.VIOLATIONS)),
                intMap(gate.get(ModelFamilyBundleGateReportFields.Gate.CONTRACT_CATEGORY_COUNTS)),
                stringList(gate.get(ModelFamilyBundleGateReportFields.Gate.CONTRACT_REMEDIATION_HINTS)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static List<String> stringList(Object violations) {
        if (!(violations instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(Object::toString)
                .toList();
    }

    private static Map<String, Integer> intMap(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        values.forEach((key, item) -> result.put(key.toString(), intValue(item)));
        return result;
    }
}
