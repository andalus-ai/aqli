package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files

private typealias SpecSmokeFixtures = DirectFixtureCoverageSmokeFixtures

object DirectFixtureCoverageSpecSmoke {
    fun requireSmokeContract(): Unit {
        val spec = DirectFixtureCoverageReportSpec
        if (!spec.moduleNameRegex.matches("${spec.moduleNamePrefix}smoke")) {
            throw GradleException("Direct fixture coverage module prefix no longer matches module regex")
        }
        if (spec.fixtureInputIncludes != listOf(
                "${spec.moduleNamePrefix}*/${spec.mainJavaSourcePath}/**/*.java",
                "${spec.moduleNamePrefix}*/${spec.testJavaSourcePath}/**/*.java",
                "${spec.moduleNamePrefix}*/${spec.fixtureRootPath}/**",
            )
        ) {
            throw GradleException("Direct fixture coverage input include spec drifted")
        }
        if (spec.directInferenceMarker.isBlank() || spec.fixtureValidatorCall.isBlank()) {
            throw GradleException("Direct fixture coverage scanner marker spec drifted")
        }
        requireFileSupportSmoke(spec)
        requireScannerCountSmoke(spec)
        requireTaskMessageSmoke()
    }

    private fun requireFileSupportSmoke(spec: DirectFixtureCoverageReportSpec): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-source-").toFile()
        try {
            val directModule = smokeDir.resolve("direct")
            val nonDirectModule = smokeDir.resolve("non-direct")
            writeJavaSource(
                directModule,
                spec.mainJavaSourcePath,
                "z/Direct.java",
                "final class Direct { static final String MODE = \"${spec.directInferenceMarker}\"; }",
            )
            writeJavaSource(
                directModule,
                spec.mainJavaSourcePath,
                "a/Before.java",
                "final class Before {}",
            )
            writeJavaSource(
                nonDirectModule,
                spec.mainJavaSourcePath,
                "Only.java",
                "final class Only {}",
            )

            if (DirectFixtureCoverageFileSupport.containsJavaSourceText(
                    nonDirectModule,
                    spec.mainJavaSourcePath,
                    spec.directInferenceMarker,
                )
            ) {
                throw GradleException("Direct fixture coverage source smoke detected a missing marker")
            }
            val directSource = DirectFixtureCoverageFileSupport.javaSourceTextIfContains(
                directModule,
                spec.mainJavaSourcePath,
                spec.directInferenceMarker,
            ) ?: throw GradleException("Direct fixture coverage source smoke missed a direct marker")
            if (DirectFixtureCoverageFileSupport.javaSourceTextIfContains(
                    nonDirectModule,
                    spec.mainJavaSourcePath,
                    spec.directInferenceMarker,
                ) != null
            ) {
                throw GradleException("Direct fixture coverage source smoke aggregated a non-direct module")
            }
            if (directSource.indexOf("final class Before") !in 0 until directSource.indexOf("final class Direct")) {
                throw GradleException("Direct fixture coverage source smoke ordering drifted")
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }

    private fun writeJavaSource(module: File, relativeDir: String, relativePath: String, text: String) {
        val sourceFile = module.resolve(relativeDir).resolve(relativePath)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(text)
    }

    private fun requireScannerCountSmoke(spec: DirectFixtureCoverageReportSpec): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-scanner-").toFile()
        try {
            val directModule = smokeDir.resolve("${spec.moduleNamePrefix}direct-smoke")
            val nonDirectModule = smokeDir.resolve("${spec.moduleNamePrefix}plain-smoke")
            writeJavaSource(
                directModule,
                spec.mainJavaSourcePath,
                "DirectSmoke.java",
                """
                final class DirectSmoke {
                    static final String MARKER = "${spec.directInferenceMarker}";
                    static final String MODEL = "${SpecSmokeFixtures.fixtureModelType}";
                    static final String ARCH = "${SpecSmokeFixtures.architecture}";
                }
                """.trimIndent(),
            )
            writeJavaSource(
                directModule,
                spec.testJavaSourcePath,
                "DirectSmokeTest.java",
                "final class DirectSmokeTest { void test() { ${spec.fixtureValidatorCall}(null); } }",
            )
            writeFixtureConfig(directModule, spec)
            writeJavaSource(
                nonDirectModule,
                spec.mainJavaSourcePath,
                "PlainSmoke.java",
                "final class PlainSmoke {}",
            )

            val scan = DirectFixtureCoverageScanner.scan(smokeDir)
            DirectFixtureCoverageGuards.requireProblemsClear(scan.problems)
            if (scan.modelModuleCount != 2 || scan.directModuleCount != 1 || scan.uniqueFixtureCount != 1) {
                throw GradleException(
                    "Direct fixture coverage scanner count smoke drifted: " +
                            "modelModuleCount=${scan.modelModuleCount}, " +
                            "directModuleCount=${scan.directModuleCount}, " +
                            "uniqueFixtureCount=${scan.uniqueFixtureCount}",
                )
            }
            if (scan.reportEntries.map { it.moduleName } != listOf(directModule.name)) {
                throw GradleException(
                    "Direct fixture coverage scanner report module smoke drifted: " +
                            "modules=${scan.reportEntries.map { it.moduleName }}",
                )
            }
            if (scan.reportEntries.single().fixtures.map { it.id } != listOf(SpecSmokeFixtures.fixtureId)) {
                throw GradleException(
                    "Direct fixture coverage scanner fixture id smoke drifted: " +
                            "fixtureIds=${scan.reportEntries.single().fixtures.map { it.id }}",
                )
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }

    private fun writeFixtureConfig(module: File, spec: DirectFixtureCoverageReportSpec) {
        val fixtureDir = module.resolve(spec.fixtureRootPath).resolve(SpecSmokeFixtures.fixtureId)
        fixtureDir.mkdirs()
        fixtureDir.resolve("config.json").writeText(
            """
            {
              "model_type": "${SpecSmokeFixtures.fixtureModelType}",
              "architectures": ["${SpecSmokeFixtures.architecture}"]
            }
            """.trimIndent(),
        )
        fixtureDir.resolve(SpecSmokeFixtures.fixtureTokenizerMarker).writeText("{}")
    }

    private fun requireTaskMessageSmoke(): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-messages-").toFile()
        try {
            val writtenReports = DirectFixtureCoverageWrittenReports(
                textReportFile = smokeDir.resolve("report.txt"),
                jsonReportFile = smokeDir.resolve("report.json"),
                schemaFile = smokeDir.resolve("report.schema.json"),
            )
            val scan = DirectFixtureCoverageScanResult(
                modelModuleCount = 2,
                directModuleCount = 1,
                uniqueFixtureCount = 1,
                reportEntries = SpecSmokeFixtures.reportEntries(),
                problems = emptyList(),
            )
            val validationSummary = DirectFixtureCoverageTaskMessages.validationSummary(
                scan = scan,
                writtenReports = writtenReports,
                projectDir = smokeDir,
            )
            if (!validationSummary.contains("1 direct model-family module")
                || !validationSummary.contains("report.txt, report.json, report.schema.json")
            ) {
                throw GradleException(
                    "Direct fixture coverage task validation summary smoke drifted: $validationSummary",
                )
            }
            val timingSummary = DirectFixtureCoverageTaskMessages.timingSummary(
                scan = scan,
                startedAt = 0,
                scannedAt = 1_000_000,
                guardedAt = 3_000_000,
                reportedAt = 6_000_000,
            )
            if (!timingSummary.contains("modules=2, directModules=1, fixtures=1")
                || !timingSummary.contains("scan=1ms, guard=2ms, report=3ms, total=6ms")
            ) {
                throw GradleException(
                    "Direct fixture coverage task timing summary smoke drifted: $timingSummary",
                )
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }
}
