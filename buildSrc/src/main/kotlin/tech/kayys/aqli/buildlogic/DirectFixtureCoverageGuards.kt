package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException

object DirectFixtureCoverageGuards {
    fun requireProblemsClear(problems: List<String>): Unit {
        if (problems.isNotEmpty()) {
            throw GradleException(buildString {
                appendLine("Direct model-family fixture coverage problems:")
                append(problems.joinToString(separator = "\n") { "- $it" })
            })
        }
    }

    fun requireReportEntries(
        directModuleCount: Int,
        uniqueFixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
    ): Int {
        if (reportEntries.size != directModuleCount) {
            throw GradleException(buildString {
                append("Direct model-family fixture report drift: directModuleCount=$directModuleCount, ")
                append("reportEntryCount=${reportEntries.size}")
            })
        }
        val fixtureCount = reportEntries.sumOf { it.fixtures.size }
        if (fixtureCount != uniqueFixtureCount) {
            throw GradleException(buildString {
                append("Direct model-family fixture report drift: fixtureCount=$fixtureCount, ")
                append("uniqueFixtureCount=$uniqueFixtureCount")
            })
        }
        val incompleteFixtureDetails = reportEntries
            .flatMap { moduleReport ->
                moduleReport.fixtures.filter { fixture ->
                    fixture.id.isBlank()
                            || fixture.modelType.isBlank()
                            || fixture.architectures.isEmpty()
                            || fixture.tokenizerMarker.isBlank()
                }.map { fixture -> "${moduleReport.moduleName}:${fixture.id.ifBlank { "<blank>" }}" }
            }
        if (incompleteFixtureDetails.isNotEmpty()) {
            throw GradleException(buildString {
                append("Direct model-family fixture report drift: incomplete fixtureDetails for ")
                append(incompleteFixtureDetails.joinToString(", "))
            })
        }
        return fixtureCount
    }
}
