package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageFileSupport {
    fun fixtureModelType(config: Map<*, *>): String =
        config["model_type"]?.toString()?.trim().orEmpty()

    fun fixtureArchitectures(config: Map<*, *>): List<String> =
        (config["architectures"] as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun containsJavaSourceText(module: File, relativeDir: String, text: String): Boolean {
        val sourceDir = sourceDir(module, relativeDir)
        if (sourceDir == null) {
            return false
        }

        return containsJavaSourceText(javaSourceFiles(sourceDir), text)
    }

    fun javaSourceTextIfContains(module: File, relativeDir: String, text: String): String? {
        val sourceDir = sourceDir(module, relativeDir)
        if (sourceDir == null) {
            return null
        }

        val sourceFiles = javaSourceFiles(sourceDir)
        if (!containsJavaSourceText(sourceFiles, text)) {
            return null
        }

        return javaSourceText(sourceFiles)
    }

    private fun sourceDir(module: File, relativeDir: String): File? =
        module.resolve(relativeDir).takeIf { it.isDirectory }

    private fun javaSourceFiles(sourceDir: File): List<File> =
        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .sortedBy { it.relativeTo(sourceDir).path }
            .toList()

    private fun containsJavaSourceText(sourceFiles: List<File>, text: String): Boolean =
        sourceFiles.any { sourceFile ->
            sourceFile.useLines { lines ->
                lines.any { it.contains(text) }
            }
        }

    private fun javaSourceText(sourceFiles: List<File>): String {
        val sourceText = StringBuilder()
        sourceFiles.forEach { sourceFile ->
            if (sourceText.isNotEmpty()) {
                sourceText.append('\n')
            }
            sourceText.append(sourceFile.readText())
        }
        return sourceText.toString()
    }
}
