package tech.kayys.aqli.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File

private typealias Spec = DirectFixtureCoverageReportSpec

object DirectFixtureCoverageReportContract {
    fun requireReportContract(report: Map<*, *>, schema: Map<*, *>): Unit {
        val problems = mutableListOf<String>()
        val schemaProperties = schema["properties"] as? Map<*, *>
        val schemaVersionProperty = schemaProperties?.get("schemaVersion") as? Map<*, *>
        val schemaModulesProperty = schemaProperties?.get("modules") as? Map<*, *>
        val schemaModuleItem = schemaModulesProperty?.get("items") as? Map<*, *>
        val schemaModuleProperties = schemaModuleItem?.get("properties") as? Map<*, *>
        val schemaFixtureDetails = schemaModuleProperties?.get("fixtureDetails") as? Map<*, *>
        val schemaFixtureDetailItem = schemaFixtureDetails?.get("items") as? Map<*, *>
        val schemaFixtureDetailProperties = schemaFixtureDetailItem?.get("properties") as? Map<*, *>
        val schemaTokenizerMarker = schemaFixtureDetailProperties?.get("tokenizerMarker") as? Map<*, *>

        if (schema["\$id"] != Spec.schemaId) {
            problems += "schema \$id should be ${Spec.schemaId}"
        }
        val schemaRootRequired = requiredFieldSet(schema["required"])
        if (schemaRootRequired != Spec.rootFieldSet) {
            problems += fieldDriftMessage("schema root required", Spec.rootFieldSet, schemaRootRequired)
        }
        if ((schemaVersionProperty?.get("const") as? Number)?.toInt() != Spec.schemaVersion) {
            problems += "schemaVersion const should be ${Spec.schemaVersion}"
        }
        val schemaModuleRequired = requiredFieldSet(schemaModuleItem?.get("required"))
        if (schemaModuleRequired != Spec.moduleFieldSet) {
            problems += fieldDriftMessage("schema module required", Spec.moduleFieldSet, schemaModuleRequired)
        }
        val schemaFixtureDetailRequired = requiredFieldSet(schemaFixtureDetailItem?.get("required"))
        if (schemaFixtureDetailRequired != Spec.detailFieldSet) {
            problems += fieldDriftMessage(
                "schema fixtureDetails required",
                Spec.detailFieldSet,
                schemaFixtureDetailRequired,
            )
        }
        if (schemaTokenizerMarker?.get("enum") != Spec.tokenizerMarkers) {
            problems += "schema tokenizerMarker enum drifted"
        }

        val reportFields = fieldSet(report)
        if (reportFields != Spec.rootFieldSet) {
            problems += fieldDriftMessage("report root", Spec.rootFieldSet, reportFields)
        }
        if ((report["schemaVersion"] as? Number)?.toInt() != Spec.schemaVersion) {
            problems += "report schemaVersion should be ${Spec.schemaVersion}"
        }
        val directModuleCount = (report["directModuleCount"] as? Number)?.toInt()
        val fixtureCount = (report["fixtureCount"] as? Number)?.toInt()
        val modules = report["modules"] as? List<*>
        if (directModuleCount == null || directModuleCount < 1) {
            problems += "report directModuleCount should be a positive integer"
        }
        if (fixtureCount == null || fixtureCount < 1) {
            problems += "report fixtureCount should be a positive integer"
        }
        if (modules == null || modules.isEmpty()) {
            problems += "report modules should be a non-empty list"
        } else {
            if (directModuleCount != modules.size) {
                problems += "report directModuleCount=$directModuleCount but modules.size=${modules.size}"
            }
            var observedFixtureCount = 0
            modules.forEach { moduleEntry ->
                val module = moduleEntry as? Map<*, *>
                if (module == null) {
                    problems += "report module entry should be an object"
                    return@forEach
                }
                val moduleFields = fieldSet(module)
                if (moduleFields != Spec.moduleFieldSet) {
                    problems += fieldDriftMessage(
                        "report module ${module["module"]}",
                        Spec.moduleFieldSet,
                        moduleFields,
                    )
                }
                val moduleName = module["module"]?.toString().orEmpty()
                if (!moduleName.matches(Spec.moduleNameRegex)) {
                    problems += "report module '$moduleName' should match aqli-model-* naming"
                }
                val moduleFixtureCount = (module["fixtureCount"] as? Number)?.toInt()
                val fixtures = (module["fixtures"] as? List<*>)?.map { it?.toString().orEmpty() }
                val fixtureDetails = module["fixtureDetails"] as? List<*>
                if (moduleFixtureCount == null || moduleFixtureCount < 1) {
                    problems += "$moduleName fixtureCount should be a positive integer"
                }
                if (fixtures == null || fixtures.any { it.isBlank() }) {
                    problems += "$moduleName fixtures should be non-blank strings"
                }
                if (fixtureDetails == null || fixtureDetails.isEmpty()) {
                    problems += "$moduleName fixtureDetails should be non-empty"
                }
                if (fixtures != null && moduleFixtureCount != fixtures.size) {
                    problems += "$moduleName fixtureCount=$moduleFixtureCount but fixtures.size=${fixtures.size}"
                }
                if (fixtureDetails != null && moduleFixtureCount != fixtureDetails.size) {
                    problems += "$moduleName fixtureCount=$moduleFixtureCount but fixtureDetails.size=${fixtureDetails.size}"
                }
                val detailIds = fixtureDetails
                    ?.mapNotNull { detail -> (detail as? Map<*, *>)?.get("id")?.toString() }
                    ?: emptyList()
                if (fixtures != null && fixtures != detailIds) {
                    problems += "$moduleName fixtures and fixtureDetails ids differ"
                }
                observedFixtureCount += moduleFixtureCount ?: 0
                fixtureDetails?.forEach { detailEntry ->
                    val detail = detailEntry as? Map<*, *>
                    if (detail == null) {
                        problems += "$moduleName fixtureDetail should be an object"
                        return@forEach
                    }
                    val detailFields = fieldSet(detail)
                    if (detailFields != Spec.detailFieldSet) {
                        problems += fieldDriftMessage(
                            "$moduleName fixtureDetail ${detail["id"]}",
                            Spec.detailFieldSet,
                            detailFields,
                        )
                    }
                    val id = detail["id"]?.toString().orEmpty()
                    val modelType = detail["modelType"]?.toString().orEmpty()
                    val architectures = (detail["architectures"] as? List<*>)?.map { it?.toString().orEmpty() }
                    val tokenizerMarker = detail["tokenizerMarker"]?.toString().orEmpty()
                    if (id.isBlank() || modelType.isBlank()) {
                        problems += "$moduleName fixtureDetail id/modelType should be non-blank"
                    }
                    if (architectures == null || architectures.isEmpty() || architectures.any { it.isBlank() }) {
                        problems += "$moduleName:$id architectures should be non-empty strings"
                    }
                    if (tokenizerMarker !in Spec.tokenizerMarkers) {
                        problems += "$moduleName:$id tokenizerMarker '$tokenizerMarker' is unsupported"
                    }
                }
            }
            if (fixtureCount != observedFixtureCount) {
                problems += "report fixtureCount=$fixtureCount but observedFixtureCount=$observedFixtureCount"
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Direct model-family fixture report contract problems:\n" +
                        problems.joinToString(separator = "\n") { "- $it" },
            )
        }
    }

    fun requireGeneratedReports(
        report: Map<*, *>,
        schema: Map<*, *>,
        reportFile: File,
        schemaFile: File,
    ): Unit {
        requireReportContract(report, schema)
        requireReportContract(
            parseJsonFileMap(reportFile),
            parseJsonFileMap(schemaFile),
        )
    }

    fun expectContractFailure(
        description: String,
        expectedMessage: String,
        report: Map<*, *>,
        schema: Map<*, *>,
    ): Unit {
        val failure = try {
            requireReportContract(report, schema)
            null
        } catch (error: GradleException) {
            error
        }
        if (failure == null) {
            throw GradleException("Expected direct fixture coverage contract failure for $description")
        }
        if (!failure.message.orEmpty().contains(expectedMessage)) {
            throw GradleException(buildString {
                append("Expected direct fixture coverage contract failure for $description to contain '$expectedMessage' ")
                append("but was: ${failure.message}")
            })
        }
    }

    private fun fieldDriftMessage(scope: String, expected: Set<String>, actual: Set<String>?): String =
        "$scope fields drifted; expected=${expected.sorted()}, actual=${actual?.sorted() ?: "<missing>"}"

    private fun fieldSet(value: Map<*, *>): Set<String> =
        value.keys.map { it.toString() }.toSet()

    private fun requiredFieldSet(value: Any?): Set<String>? =
        (value as? List<*>)?.map { it.toString() }?.toSet()

    private fun parseJsonFileMap(file: File): Map<*, *> =
        JsonSlurper().parse(file) as Map<*, *>
}
