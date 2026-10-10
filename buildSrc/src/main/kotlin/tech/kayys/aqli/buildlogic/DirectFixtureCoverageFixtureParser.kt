package tech.kayys.aqli.buildlogic

import groovy.json.JsonSlurper
import java.io.File

object DirectFixtureCoverageFixtureParser {
    fun parse(
        module: File,
        mainJavaText: String,
        config: File,
        problems: MutableList<String>,
    ): DirectFixtureCoverage? {
        val fixturePath = config.relativeTo(module).path
        val parsed = parseConfig(module, fixturePath, config, problems)
        if (parsed != null) {
            validateClaims(module, mainJavaText, fixturePath, parsed, problems)
        }

        val dir = config.parentFile
        val tokenizerMarker = DirectFixtureCoverageReportSpec.tokenizerMarkers.firstOrNull {
            dir.resolve(it).isFile
        }
        if (tokenizerMarker == null) {
            problems += "${module.name}: ${dir.relativeTo(module).path} should include tokenizer.json or vocab marker"
        }
        return parsed?.let {
            DirectFixtureCoverage(
                id = dir.name,
                modelType = DirectFixtureCoverageFileSupport.fixtureModelType(it),
                architectures = DirectFixtureCoverageFileSupport.fixtureArchitectures(it),
                tokenizerMarker = tokenizerMarker.orEmpty(),
            )
        }
    }

    private fun parseConfig(
        module: File,
        fixturePath: String,
        config: File,
        problems: MutableList<String>,
    ): Map<*, *>? =
        try {
            JsonSlurper().parse(config) as? Map<*, *>
        } catch (error: Exception) {
            problems += "${module.name}: $fixturePath is not parseable JSON (${error.javaClass.simpleName})"
            null
        }

    private fun validateClaims(
        module: File,
        mainJavaText: String,
        fixturePath: String,
        parsed: Map<*, *>,
        problems: MutableList<String>,
    ): Unit {
        val modelType = DirectFixtureCoverageFileSupport.fixtureModelType(parsed)
        if (modelType.isBlank()) {
            problems += "${module.name}: $fixturePath should declare model_type"
        } else if (!mainJavaText.contains("\"$modelType\"")) {
            problems += "${module.name}: $fixturePath model_type '$modelType' is not claimed in main Java sources"
        }
        val architectures = parsed["architectures"] as? List<*>
        if (architectures.isNullOrEmpty()) {
            problems += "${module.name}: $fixturePath should declare at least one architecture"
        } else {
            val architectureClaims = DirectFixtureCoverageFileSupport.fixtureArchitectures(parsed)
            if (architectureClaims.isEmpty()) {
                problems += "${module.name}: $fixturePath should declare at least one non-blank architecture"
            }
            architectureClaims.forEach { architecture ->
                if (!mainJavaText.contains("\"$architecture\"")) {
                    problems += "${module.name}: $fixturePath architecture '$architecture' is not claimed in main Java sources"
                }
            }
        }
    }
}
