package de.fayard.buildSrcLibs

import com.squareup.kotlinpoet.FileSpec
import de.fayard.buildSrcLibs.BuildSrcLibsTask.Companion.findDependencies
import de.fayard.buildSrcLibs.internal.*
import de.fayard.buildSrcLibs.internal.Deps
import de.fayard.buildSrcLibs.internal.PluginConfig
import de.fayard.buildSrcLibs.internal.checkModeAndNames
import de.fayard.refreshVersions.core.internal.OutputFile
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion

@Suppress("UnstableApiUsage")
open class VersionsCatalogTask : DefaultTask() {

    @TaskAction
    fun taskActionEnableSupport() {
        if (GradleVersion.current() < GradleVersion.version("7.0")) {
            error("""
                |Gradle versions catalogs are only supported in Gradle 7+
                |Upgrade first with this command
                |     ./gradlew wrapper --gradle-version 7.0
            """.trimMargin())
        }
        OutputFile.checkWhichFilesExist(project.rootDir)
        val outputDir = project.file(OutputFile.OUTPUT_DIR.path)
        // Enable Gradle's version catalog support
        // https://docs.gradle.org/current/userguide/platforms.html
        val file = OutputFile.SETTINGS
        if (file.existed.not()) return
        val settingsText = file.readText(project)
        val alreadyConfigured = settingsText.lines().any { it.containsVersionsCatalogDeclaration() }
        if (!alreadyConfigured) {
            val newText = ("""
                |${settingsText}
                |enableFeaturePreview("VERSION_CATALOGS")
                |""".trimMargin())
            file.writeText(newText, project)
            file.logFileWasModified()
        }

    }


    @TaskAction
    fun taskUpdateVersionsCatalog() {
        val catalog = OutputFile.GRADLE_VERSIONS_CATALOG

        val allDependencies = project.findDependencies()
        val resolvedUseFqdn: List<String> = PluginConfig.computeUseFqdnFor(
            libraries = allDependencies,
            configured = emptyList(),
            byDefault = PluginConfig.MEANING_LESS_NAMES
        )
        val deps: Deps = allDependencies.checkModeAndNames(resolvedUseFqdn)
        catalog.writeText(versionsCatalog(deps), project)
        catalog.logFileWasModified()
    }

    companion object {
        internal fun versionsCatalog(deps: Deps): String = buildString {
            append(
                """
                |## Generated by $ ./gradlew refreshVersionsCatalog
                |[libraries]
                |""".trimMargin()
            )

            deps.libraries.forEach {
                append(deps.names[it])
                append(" = \"")
                append(it.groupModuleUnderscore())
                append('"')
                append("\n")
            }
            append("\n")
        }

        fun String.containsVersionsCatalogDeclaration(): Boolean {
            return this.substringBefore("//").contains("enableFeaturePreview.*VERSION_CATALOGS".toRegex())
        }
    }
}

