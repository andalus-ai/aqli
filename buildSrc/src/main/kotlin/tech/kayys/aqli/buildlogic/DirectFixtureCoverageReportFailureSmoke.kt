package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException

object DirectFixtureCoverageReportFailureSmoke {
    private const val fixtureCountDriftDescription = "fixture count drift"
    private const val fixtureCountDriftMessage = "report fixtureCount=2 but observedFixtureCount=1"
    private const val fixtureIdDetailDriftDescription = "fixture id/detail drift"
    private const val fixtureIdDetailDriftMessage = "fixtures and fixtureDetails ids differ"
    private const val unsupportedTokenizerDescription = "unsupported tokenizer marker"
    private val unsupportedTokenizerMessage =
        "tokenizerMarker '${DirectFixtureCoverageSmokeFixtures.unsupportedTokenizerMarker}' is unsupported"
    private const val incompleteFixtureDetailsMessage = "incomplete fixtureDetails"

    fun requireSmokeContract(reportPayload: Map<String, Any>, schemaPayload: Map<String, Any>): Unit {
        contractFailureCases(reportPayload).forEach { failureCase ->
            expectContractFailure(failureCase, schemaPayload)
        }
        requireIncompleteReportEntryFailure()
    }

    private fun contractFailureCases(reportPayload: Map<String, Any>): List<ContractFailureCase> =
        listOf(
            fixtureCountDriftCase(reportPayload),
            fixtureIdDetailDriftCase(reportPayload),
            unsupportedTokenizerCase(reportPayload),
        )

    private fun fixtureCountDriftCase(reportPayload: Map<String, Any>): ContractFailureCase =
        ContractFailureCase(
            description = fixtureCountDriftDescription,
            expectedMessage = fixtureCountDriftMessage,
            report = reportPayload + ("fixtureCount" to 2),
        )

    private fun fixtureIdDetailDriftCase(reportPayload: Map<String, Any>): ContractFailureCase =
        ContractFailureCase(
            description = fixtureIdDetailDriftDescription,
            expectedMessage = fixtureIdDetailDriftMessage,
            report = reportPayload + ("modules" to listOf(
                DirectFixtureCoverageSmokeFixtures.mismatchedFixtureDetailModuleMap(),
            )),
        )

    private fun unsupportedTokenizerCase(reportPayload: Map<String, Any>): ContractFailureCase =
        ContractFailureCase(
            description = unsupportedTokenizerDescription,
            expectedMessage = unsupportedTokenizerMessage,
            report = reportPayload + ("modules" to listOf(
                DirectFixtureCoverageSmokeFixtures.unsupportedTokenizerModuleMap(),
            )),
        )

    private fun requireIncompleteReportEntryFailure(): Unit {
        val incompleteFailure = try {
            DirectFixtureCoverageGuards.requireReportEntries(
                directModuleCount = 1,
                uniqueFixtureCount = 1,
                reportEntries = DirectFixtureCoverageSmokeFixtures.incompleteReportEntries(),
            )
            null
        } catch (error: GradleException) {
            error
        }
        if (!incompleteFailure?.message.orEmpty().contains(incompleteFixtureDetailsMessage)) {
            throw GradleException("Direct fixture coverage report entry smoke failure drifted")
        }
    }

    private fun expectContractFailure(failureCase: ContractFailureCase, schema: Map<*, *>): Unit =
        DirectFixtureCoverageReportContract.expectContractFailure(
            description = failureCase.description,
            expectedMessage = failureCase.expectedMessage,
            report = failureCase.report,
            schema = schema,
        )

    private data class ContractFailureCase(
        val description: String,
        val expectedMessage: String,
        val report: Map<*, *>,
    )
}
