package dev.echonine.kite.scripting

import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.dependencies.resolveFromScriptSourceAnnotations
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvm.JvmDependency
import kotlinx.coroutines.runBlocking

class AnnotationProcessor : RefineScriptCompilationConfigurationHandler {
    private val resolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), MavenDependenciesResolver())

    override fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations =
            context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
                ?: return context.compilationConfiguration.asSuccess()


        val scriptBaseDir = (context.script as? FileBasedScriptSource)?.file?.parentFile
        val importedSources = annotations.flatMap {
            (it as? Import)?.paths?.map { sourceName ->
                FileScriptSource(scriptBaseDir?.resolve(sourceName) ?: File(sourceName))
            } ?: emptyList()
        }
        val compileOptions = annotations.flatMap {
            (it as? CompilerOptions)?.options?.toList() ?: emptyList()
        }

        // Handle dependency resolution for DependsOn and Repository annotations
        val dependencyResult = runBlocking {
            resolver.resolveFromScriptSourceAnnotations(annotations)
        }

        return dependencyResult.onSuccess { resolvedDependencies ->
            context.compilationConfiguration.with {
                if (importedSources.isNotEmpty()) {
                    importScripts.append(importedSources)
                }
                if (compileOptions.isNotEmpty()) {
                    compilerOptions.append(compileOptions)
                }
                if (resolvedDependencies.isNotEmpty()) {
                    dependencies.append(JvmDependency(resolvedDependencies))
                }
            }.asSuccess()
        }.onFailure {
            // Even if dependency resolution fails, apply other annotations
            context.compilationConfiguration.with {
                if (importedSources.isNotEmpty()) {
                    importScripts.append(importedSources)
                }
                if (compileOptions.isNotEmpty()) {
                    compilerOptions.append(compileOptions)
                }
            }.asSuccess()
        }
    }
}