package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException
import java.nio.file.Files

private typealias SmokeSpec = DirectFixtureCoverageReportSpec
private typealias SmokeFixtures = DirectFixtureCoverageSmokeFixtures

object DirectFixtureCoverageReportSmoke {
    fun requireSmokeContract(): Unit {
        DirectFixtureCoverageSpecSmoke.requireSmokeContract()
        val smokeReportEntries = SmokeFixtures.reportEntries()
        val reportPayload = DirectFixtureCoverageReportRendering.reportPayload(
            directModuleCount = 1,
            fixtureCount = 1,
            reportEntries = smokeReportEntries,
        )
        val schemaPayload = DirectFixtureCoverageReportRendering.schemaPayload()

        requireGuardSmoke(smokeReportEntries)
        requireTextReportSmoke(smokeReportEntries)
        requireReportContractSmoke(reportPayload, schemaPayload)
        requireGeneratedReportSmoke(reportPayload, schemaPayload)
        requireChangeAwareWriterSmoke(smokeReportEntries)
        DirectFixtureCoverageReportFailureSmoke.requireSmokeContract(reportPayload, schemaPayload)
    }

    private fun requireGuardSmoke(smokeReportEntries: List<DirectFixtureCoverageModuleReport>): Unit {
        DirectFixtureCoverageGuards.requireProblemsClear(emptyList())
        val coverageFailure = try {
            DirectFixtureCoverageGuards.requireProblemsClear(
                listOf("${SmokeFixtures.moduleName}: missing fixture"),
            )
            null
        } catch (error: GradleException) {
            error
        }
        if (!coverageFailure?.message.orEmpty().contains("- ${SmokeFixtures.moduleName}: missing fixture")) {
            throw GradleException("Direct fixture coverage problem smoke failure drifted")
        }
        val fixtureCount = DirectFixtureCoverageGuards.requireReportEntries(
            directModuleCount = 1,
            uniqueFixtureCount = 1,
            reportEntries = smokeReportEntries,
        )
        if (fixtureCount != 1) {
            throw GradleException("Direct fixture coverage report entry smoke count drifted")
        }
    }

    private fun requireTextReportSmoke(smokeReportEntries: List<DirectFixtureCoverageModuleReport>): Unit {
        val textReport = DirectFixtureCoverageReportRendering.textReport(
            directModuleCount = 1,
            fixtureCount = 1,
            reportEntries = smokeReportEntries,
        )
        if (!textReport.contains("schemaVersion=${SmokeSpec.schemaVersion}")
            || !textReport.contains("${SmokeFixtures.moduleName} (1): ${SmokeFixtures.fixtureId}")
            || !textReport.contains(
                "model_type=${SmokeFixtures.fixtureModelType}, architectures=${SmokeFixtures.architecture}, " +
                        "tokenizer=${SmokeFixtures.fixtureTokenizerMarker}",
            )
        ) {
            throw GradleException("Direct fixture coverage text report smoke output drifted")
        }
    }

    private fun requireReportContractSmoke(reportPayload: Map<String, Any>, schemaPayload: Map<String, Any>): Unit {
        DirectFixtureCoverageReportContract.requireReportContract(reportPayload, schemaPayload)
        DirectFixtureCoverageReportContract.requireReportContract(
            DirectFixtureCoverageReportRendering.jsonRoundTripMap(reportPayload),
            DirectFixtureCoverageReportRendering.jsonRoundTripMap(schemaPayload),
        )
    }

    private fun requireGeneratedReportSmoke(reportPayload: Map<String, Any>, schemaPayload: Map<String, Any>): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-coverage-").toFile()
        try {
            val smokeReportFile = smokeDir.resolve("model-family-direct-fixtures.json")
            val smokeSchemaFile = smokeDir.resolve("model-family-direct-fixtures.schema.json")
            smokeReportFile.writeText(DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(reportPayload))
            smokeSchemaFile.writeText(DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(schemaPayload))
            DirectFixtureCoverageReportContract.requireGeneratedReports(
                report = reportPayload,
                schema = schemaPayload,
                reportFile = smokeReportFile,
                schemaFile = smokeSchemaFile,
            )
        } finally {
            smokeDir.deleteRecursively()
        }
    }

    private fun requireChangeAwareWriterSmoke(
        smokeReportEntries: List<DirectFixtureCoverageModuleReport>,
    ): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-writer-").toFile()
        try {
            val textReportFile = smokeDir.resolve("model-family-direct-fixtures.txt")
            val jsonReportFile = smokeDir.resolve("model-family-direct-fixtures.json")
            val schemaFile = smokeDir.resolve("model-family-direct-fixtures.schema.json")
            val reportFiles = listOf(textReportFile, jsonReportFile, schemaFile)
            DirectFixtureCoverageReportWriter.writeReports(
                directModuleCount = 1,
                fixtureCount = 1,
                reportEntries = smokeReportEntries,
                textReportFile = textReportFile,
                jsonReportFile = jsonReportFile,
                schemaFile = schemaFile,
            )
            val pinnedModifiedAt = 1_700_000_000_000L
            reportFiles.forEach { reportFile ->
                if (!reportFile.setLastModified(pinnedModifiedAt)) {
                    throw GradleException("Direct fixture coverage writer smoke could not pin report timestamp")
                }
            }

            DirectFixtureCoverageReportWriter.writeReports(
                directModuleCount = 1,
                fixtureCount = 1,
                reportEntries = smokeReportEntries,
                textReportFile = textReportFile,
                jsonReportFile = jsonReportFile,
                schemaFile = schemaFile,
            )
            if (reportFiles.any { it.lastModified() != pinnedModifiedAt }) {
                throw GradleException("Direct fixture coverage writer rewrote unchanged reports")
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }
}
