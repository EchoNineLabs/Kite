package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.configuration.KiteCompilationConfiguration
import dev.echonine.kite.scripting.configuration.KiteEvaluationConfiguration
import dev.echonine.kite.scripting.configuration.KiteScriptingHostConfiguration
import dev.echonine.kite.util.extensions.errorRich
import dev.echonine.kite.util.extensions.infoRich
import dev.echonine.kite.util.extensions.nameWithoutExtensions
import dev.echonine.kite.util.extensions.warnRich
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.jetbrains.annotations.Unmodifiable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic.Severity
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal class ScriptManager(val plugin: Kite) {
    private val logger = ComponentLogger.logger("Kite")
    private val loadedScripts = mutableMapOf<String, ScriptContext>()
    private val scriptingHost = BasicJvmScriptingHost(KiteScriptingHostConfiguration)

    /**
     * Returns an unmodifiable view of all loaded scripts.
     */
    fun getLoadedScripts(): @Unmodifiable Map<String, ScriptContext> = loadedScripts.toSortedMap()

    /**
     * Returns true if a script with the specified name is currently loaded.
     */
    fun isScriptLoaded(name: String): Boolean = loadedScripts.containsKey(name)

    /**
     * Collects all available script files to a list and returns it.
     */
    fun gatherAvailableScriptFiles(): List<ScriptHolder> {
        // Creating scripts directory in case it does not exist.
        Kite.Structure.SCRIPTS_DIR.mkdirs()
        // Iterating over all files inside scripts directory.
        return Kite.Structure.SCRIPTS_DIR.listFiles()
            ?.mapNotNull { ScriptHolder.fromName(it.nameWithoutExtensions, Kite.Structure.SCRIPTS_DIR) }
            ?.distinctBy { it.name }
            ?.sortedBy { it.name }
            ?.toList() ?: emptyList()
    }

    /**
     * Compiles and loads all available scripts.
     */
    fun loadAll() {
        val scriptHolders = gatherAvailableScriptFiles()
        logger.infoRich("Compiling <yellow>${scriptHolders.size} <reset>script(s)...")
        val (compiledScripts, elapsedCompilationTime) = measureTimedValue {
            runBlocking {
                scriptHolders.mapNotNull { compileScriptAsync(it) }
            }
        }
        logger.infoRich("Successfully compiled <yellow>${compiledScripts.size} <reset>out of total <yellow>${scriptHolders.size}<reset> scripts. Loading them now...")
        // Loading compiled scripts.
        val elapsedExecutionTime = measureTime {
            for (script in compiledScripts) try {
                loadedScripts[script.name] = script
                script.runOnLoad()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        logger.infoRich("Successfully loaded <yellow>${loadedScripts.size} <reset>out of total <yellow>${compiledScripts.size}<reset> scripts.")
        logger.infoRich("(Compilation: <yellow>${elapsedCompilationTime.inWholeMilliseconds}ms</yellow>, Execution: <yellow>${elapsedExecutionTime.inWholeMilliseconds}ms</yellow>)")
    }

    /**
     * Compiles script backed by the specified [ScriptHolder] instance and returns the result.
     * In case compilation fails, a `null` is returned instead.
     */
    private suspend fun compileScriptAsync(holder: ScriptHolder): ScriptContext? = withContext(Dispatchers.IO) {
        // Creating a new instance of ScriptContext. Name of the script either file name with no extensions or script's folder name.
        val script = ScriptContext(holder.name, holder.entryPoint)
        val wasCacheInvalidated = AtomicBoolean(false)
        // Creating CompilationConfiguration based on KiteCompilationConfiguration template.
        val compilationConfiguration = KiteCompilationConfiguration.with {
            displayName(script.name)
            refineConfiguration {
                beforeCompiling { context ->
                    return@beforeCompiling ScriptCompilationConfiguration(context.compilationConfiguration) {
                        runBlocking {
                            // Invalidating the cache. We need to make sure it will be triggered only once, for the main script.
                            // Imports cache will then be populated with new entries in the next step.
                            // Since this callback runs for *all* scripts - including @Import-ed ones - imports will be included recursively.
                            if (wasCacheInvalidated.get() == false) {
                                Kite.IMPORTS_CACHE!!.invalidate(script.name)
                                wasCacheInvalidated.set(true)
                            }
                            // Getting all imported scripts added to the configuration via annotation processor.
                            val imports = context.compilationConfiguration[ScriptCompilationConfiguration.importScripts]
                            // Writing non-empty imported script paths to the imports cache.
                            imports?.mapNotNull { (it as? FileScriptSource)?.file?.canonicalPath }?.also {
                                Kite.IMPORTS_CACHE?.append(script.name, it)
                            }
                        }
                    }.asSuccess()
                }
            }
        }
        // Creating EvaluationConfiguration based on KiteEvaluationConfiguration template.
        val evaluationConfiguration = KiteEvaluationConfiguration.with {
            jvm {
                baseClassLoader(Kite::class.java.classLoader)
            }
            implicitReceivers(script)
            providedProperties(mapOf(
                "plugin" to plugin,
                "server" to plugin.server
            ))
        }
        // Compiling/loading the script. Measure operation time to verify cache performance.
        val (compiledScript, elapsedTime) = measureTimedValue {
            scriptingHost.eval(FileScriptSource(holder.entryPoint), compilationConfiguration, evaluationConfiguration)
        }
        // Logging message after *failed* compilation, but before diagnostics.
        if (compiledScript is ResultWithDiagnostics.Failure) {
            val errorCount = compiledScript.reports.count { it.severity == Severity.FATAL || it.severity == Severity.ERROR }
            logger.errorRich("Script <yellow>${holder.name}</yellow> reported <yellow>$errorCount</yellow> error(s) during compilation:")
        }
        // Logging diagnostics to the console.
        compiledScript.reports.forEach {
            val message = "<yellow>(${script.name}, ${it.sourcePath?.substringAfterLast("/")}, line ${it.location?.start?.line ?: "???"}, col ${it.location?.start?.col ?: "???"})</yellow> ${it.message}"
            when (it.severity) {
                Severity.INFO -> logger.infoRich(message)
                Severity.WARNING -> logger.warnRich(message)
                Severity.ERROR,
                Severity.FATAL -> logger.errorRich(message)
                else -> {}
            }
        }
        // Logging message after *successful* compilation, and after diagnostics.
        if (compiledScript is ResultWithDiagnostics.Success) {
            if (!wasCacheInvalidated.get()) {
                logger.infoRich("Compiled <yellow>${holder.name}</yellow> from cache in <yellow>${elapsedTime.inWholeMilliseconds}ms</yellow>.")
            } else {
                logger.infoRich("Compiled <yellow>${holder.name}</yellow> in <yellow>${elapsedTime.inWholeMilliseconds}ms</yellow>.")
            }
        }
        // Returning ScriptContext instance if compilation was successful, or null otherwise.
        return@withContext if (compiledScript !is ResultWithDiagnostics.Failure) script else null
    }

    /**
     * Compiles and loads a script with the specified name.
     * Name must be either the script's file name or a name of a directory containing a `main.kite.kts` file.
     */
    suspend fun load(name: String): Boolean {
        // Finding script by the specified name. Returning false if not found.
        val holder = ScriptHolder.fromName(name, Kite.Structure.SCRIPTS_DIR) ?: return false
        // Returning false if already loaded.
        if (loadedScripts.containsKey(holder.name))
            return false
        // Loading the script.
        return load(holder)
    }

    // Compiles and loads specified script holder.
    private suspend fun load(holder: ScriptHolder): Boolean = suspendCancellableCoroutine { coroutine ->
        CoroutineScope(Dispatchers.Default).launch {
            try {
                compileScriptAsync(holder)?.let { script ->
                    plugin.server.globalRegionScheduler.execute(plugin, {
                        loadedScripts[script.name] = script
                        script.runOnLoad()
                        logger.infoRich("Script <yellow>${holder.name}</yellow> has been successfully loaded.")
                        // Resuming the coroutine.
                        coroutine.resume(true)
                    })
                } ?: coroutine.resume(false)
            } catch (e: Exception) {
                // Logging error(s) to the console and returning null.
                logger.errorRich("Script <yellow>${holder.name}</yellow> reported errors during compilation.")
                logger.errorRich("  (1) ${e.javaClass.name}: ${e.message}")
                if (e.cause != null)
                    logger.errorRich("  (2) ${e.cause!!.javaClass.name}: ${e.cause!!.message}")
                // Resuming the coroutine.
                coroutine.resume(false)
            }
        }
    }

    /**
     * Unloads a specified script by its name. Returns `false` if no such script is currently loaded.
     */
    suspend fun unload(name: String): Boolean {
        return loadedScripts[name]?.let {
            return unload(it)
        } ?: false
    }

    // Unloads specified script by it's context.
    private suspend fun unload(script: ScriptContext): Boolean = suspendCancellableCoroutine { coroutine ->
        plugin.server.globalRegionScheduler.execute(plugin, {
            script.runOnUnload()
            script.cleanup()
            loadedScripts.remove(script.name)
            // Resuming the coroutine.
            coroutine.resume(true)
        })
    }

}
