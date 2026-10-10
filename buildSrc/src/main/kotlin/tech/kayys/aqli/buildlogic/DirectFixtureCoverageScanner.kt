package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageScanner {
    fun scan(modelModulesDir: File): DirectFixtureCoverageScanResult {
        val modules = modelModulesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(DirectFixtureCoverageReportSpec.moduleNamePrefix) }
            ?.sortedBy { it.name }
            ?: emptyList()

        val problems = mutableListOf<String>()
        val reportEntries = mutableListOf<DirectFixtureCoverageModuleReport>()
        val fixtureOwners = linkedMapOf<String, String>()
        var directModuleCount = 0

        modules.forEach { module ->
            val mainJavaText = DirectFixtureCoverageFileSupport.javaSourceTextIfContains(
                module,
                DirectFixtureCoverageReportSpec.mainJavaSourcePath,
                DirectFixtureCoverageReportSpec.directInferenceMarker,
            )
            if (mainJavaText == null) {
                return@forEach
            }

            directModuleCount += 1
            scanDirectModule(
                module = module,
                mainJavaText = mainJavaText,
                hasFixtureValidatorCall = DirectFixtureCoverageFileSupport.containsJavaSourceText(
                    module,
                    DirectFixtureCoverageReportSpec.testJavaSourcePath,
                    DirectFixtureCoverageReportSpec.fixtureValidatorCall,
                ),
                fixtureOwners = fixtureOwners,
                reportEntries = reportEntries,
                problems = problems,
            )
        }
        if (directModuleCount == 0) {
            problems += "no direct-safetensor model-family modules were detected under $modelModulesDir"
        }

        return DirectFixtureCoverageScanResult(
            modelModuleCount = modules.size,
            directModuleCount = directModuleCount,
            uniqueFixtureCount = fixtureOwners.size,
            reportEntries = reportEntries,
            problems = problems,
        )
    }

    private fun scanDirectModule(
        module: File,
        mainJavaText: String,
        hasFixtureValidatorCall: Boolean,
        fixtureOwners: MutableMap<String, String>,
        reportEntries: MutableList<DirectFixtureCoverageModuleReport>,
        problems: MutableList<String>,
    ): Unit {
        val fixtureRoot = module.resolve(DirectFixtureCoverageReportSpec.fixtureRootPath)
        val fixtureConfigs = fixtureConfigs(fixtureRoot)
        if (fixtureConfigs.isEmpty()) {
            problems += "${module.name}: missing ${DirectFixtureCoverageReportSpec.fixtureRootPath}/*/config.json"
        } else {
            registerFixtureOwners(module, fixtureConfigs, fixtureOwners, problems)
        }

        val moduleFixtureDetails = fixtureConfigs.mapNotNull { config ->
            DirectFixtureCoverageFixtureParser.parse(module, mainJavaText, config, problems)
        }
        if (fixtureConfigs.isNotEmpty()) {
            reportEntries += DirectFixtureCoverageModuleReport(
                moduleName = module.name,
                fixtures = moduleFixtureDetails.sortedBy { it.id },
            )
        }

        if (!hasFixtureValidatorCall) {
            problems += "${module.name}: tests should call ${DirectFixtureCoverageReportSpec.fixtureValidatorCall}"
        }
    }

    private fun registerFixtureOwners(
        module: File,
        fixtureConfigs: List<File>,
        fixtureOwners: MutableMap<String, String>,
        problems: MutableList<String>,
    ): Unit {
        fixtureConfigs.map { it.parentFile.name }.forEach { fixtureName ->
            val previousOwner = fixtureOwners.putIfAbsent(fixtureName, module.name)
            if (previousOwner != null) {
                problems += "fixture '$fixtureName' is declared by both $previousOwner and ${module.name}"
            }
        }
    }

    private fun fixtureConfigs(fixtureRoot: File): List<File> {
        if (!fixtureRoot.exists()) {
            return emptyList()
        }

        return fixtureRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.map { it.resolve("config.json") }
            ?.filter { it.isFile }
            ?.sortedBy { it.parentFile.name }
            ?.toList()
            ?: emptyList()
    }
}
