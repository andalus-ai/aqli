package tech.kayys.aqli.buildlogic

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

object DirectFixtureCoverageReportRendering {
    fun prettyJsonWithTrailingNewline(value: Any?): String =
        buildString {
            appendLine(JsonOutput.prettyPrint(JsonOutput.toJson(value)))
        }

    fun jsonRoundTripMap(value: Any?): Map<*, *> =
        JsonSlurper().parseText(prettyJsonWithTrailingNewline(value)) as Map<*, *>

    fun reportPayload(
        directModuleCount: Int,
        fixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
    ): Map<String, Any> = mapOf(
        "schemaVersion" to DirectFixtureCoverageReportSpec.schemaVersion,
        "directModuleCount" to directModuleCount,
        "fixtureCount" to fixtureCount,
        "modules" to reportEntries
            .sortedBy { it.moduleName }
            .map { moduleReport ->
                mapOf(
                    "module" to moduleReport.moduleName,
                    "fixtureCount" to moduleReport.fixtures.size,
                    "fixtures" to moduleReport.fixtures.map { it.id },
                    "fixtureDetails" to moduleReport.fixtures.map { fixture ->
                        mapOf(
                            "id" to fixture.id,
                            "modelType" to fixture.modelType,
                            "architectures" to fixture.architectures,
                            "tokenizerMarker" to fixture.tokenizerMarker,
                        )
                    },
                )
            },
    )

    fun textReport(
        directModuleCount: Int,
        fixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
    ): String = buildString {
        appendLine("Direct model-family fixture coverage")
        appendLine("schemaVersion=${DirectFixtureCoverageReportSpec.schemaVersion}")
        appendLine("directModuleCount=$directModuleCount")
        appendLine("fixtureCount=$fixtureCount")
        appendLine()
        reportEntries
            .sortedBy { it.moduleName }
            .forEach { moduleReport ->
                appendLine(
                    "${moduleReport.moduleName} (${moduleReport.fixtures.size}): " +
                            moduleReport.fixtures.joinToString(", ") { it.id },
                )
                moduleReport.fixtures.forEach { fixture ->
                    appendLine(buildString {
                        append("  - ${fixture.id}: model_type=${fixture.modelType}, ")
                        append("architectures=${fixture.architectures.joinToString("|")}, ")
                        append("tokenizer=${fixture.tokenizerMarker}")
                    })
                }
            }
    }

    fun schemaPayload(): Map<String, Any> = mapOf(
        "\$schema" to "https://json-schema.org/draft/2020-12/schema",
        "\$id" to DirectFixtureCoverageReportSpec.schemaId,
        "title" to "Aqli direct model-family fixture coverage report",
        "type" to "object",
        "additionalProperties" to false,
        "required" to DirectFixtureCoverageReportSpec.rootFields,
        "properties" to mapOf(
            "schemaVersion" to mapOf("const" to DirectFixtureCoverageReportSpec.schemaVersion),
            "directModuleCount" to mapOf("type" to "integer", "minimum" to 1),
            "fixtureCount" to mapOf("type" to "integer", "minimum" to 1),
            "modules" to mapOf(
                "type" to "array",
                "minItems" to 1,
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to DirectFixtureCoverageReportSpec.moduleFields,
                    "properties" to mapOf(
                        "module" to mapOf(
                            "type" to "string",
                            "pattern" to DirectFixtureCoverageReportSpec.moduleNamePattern,
                        ),
                        "fixtureCount" to mapOf("type" to "integer", "minimum" to 1),
                        "fixtures" to mapOf(
                            "type" to "array",
                            "minItems" to 1,
                            "items" to mapOf("type" to "string", "minLength" to 1),
                        ),
                        "fixtureDetails" to mapOf(
                            "type" to "array",
                            "minItems" to 1,
                            "items" to mapOf(
                                "type" to "object",
                                "additionalProperties" to false,
                                "required" to DirectFixtureCoverageReportSpec.detailFields,
                                "properties" to mapOf(
                                    "id" to mapOf("type" to "string", "minLength" to 1),
                                    "modelType" to mapOf("type" to "string", "minLength" to 1),
                                    "architectures" to mapOf(
                                        "type" to "array",
                                        "minItems" to 1,
                                        "items" to mapOf("type" to "string", "minLength" to 1),
                                    ),
                                    "tokenizerMarker" to mapOf(
                                        "type" to "string",
                                        "enum" to DirectFixtureCoverageReportSpec.tokenizerMarkers,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
