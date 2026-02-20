package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import dev.echonine.kite.util.extensions.errorRich
import dev.echonine.kite.util.extensions.infoRich
import dev.echonine.kite.util.extensions.nameWithoutExtensions
import dev.echonine.kite.util.extensions.warnRich
import dev.echonine.kite.scripting.configuration.KiteCompilationConfiguration
import dev.echonine.kite.scripting.configuration.KiteEvaluationConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.jetbrains.annotations.Unmodifiable
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic.Severity
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.time.measureTimedValue

internal class ScriptManager(val plugin: Kite) {

    private val logger = ComponentLogger.logger("Kite")
    private val loadedScripts = mutableMapOf<String, ScriptContext>()
    private val logExecutor = Executors.newSingleThreadExecutor()
    private val scriptingHost = BasicJvmScriptingHost()

    /**
     * Returns an unmodifiable view of all loaded scripts.
     */
    fun getLoadedScripts(): @Unmodifiable Map<String, ScriptContext> = loadedScripts.toMap()

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
        // Otherwise, iterating over all files inside scripts directory.
        return Kite.Structure.SCRIPTS_DIR.listFiles()
            ?.mapNotNull { ScriptHolder.fromName(it.nameWithoutExtensions, Kite.Structure.SCRIPTS_DIR) }
            ?.distinctBy { it.name }
            ?.toList() ?: emptyList()
    }

    /**
     * Compiles and loads all available scripts.
     */
    fun loadAll() {
        val scriptHolders = gatherAvailableScriptFiles()
        logger.infoRich("Compiling <yellow>${scriptHolders.size} <reset>script(s)...")
        // Compiling all available scripts in parallel.
        val compiledScripts = runBlocking {
            // primeScriptingEngine()
            scriptHolders.map { holder -> async(Dispatchers.Default) {
                try {
                    compileScriptAsync(holder)
                } catch (e: Throwable) {
                    // Logging error(s) to the console and returning null.
                    logger.errorRich("Script <yellow>${holder.name}</yellow> reported error(s) during compilation.")
                    logger.errorRich("  (1) ${e.javaClass.name}: ${e.message}")
                    if (e.cause != null)
                        logger.errorRich("  (2) ${e.cause!!.javaClass.name}: ${e.cause!!.message}")
                    return@async null
                }
            }}.awaitAll().filterNotNull()
        }
        logExecutor.submit {
            logger.infoRich("Loading <yellow>${compiledScripts.size}<reset> out of <yellow>${scriptHolders.size}<reset> scripts...")
        }
        // Calling 'onLoad' for each compiled script and adding it to the list of loaded scripts.
        compiledScripts.forEach { script ->
            loadedScripts[script.name] = script
            script.runOnLoad()
            // Logging message(s) from a single-thread queue to keep correct order.
            logExecutor.submit {
                logger.infoRich("Script <yellow>${script.name} <reset>has been successfully loaded.")
            }
        }
        // Logging message(s) from a single-thread queue to keep the correct order.
        logExecutor.submit {
            logger.infoRich("Successfully loaded <yellow>${loadedScripts.size} <reset>out of total <yellow>${scriptHolders.size}<reset> scripts.")
        }
    }

    // // Primes the scripting engine by compiling a simple hello world script.
    // private suspend fun primeScriptingEngine() = withContext(Dispatchers.IO) {
    //     logger.infoRich("Priming scripting engine...")
    //     val helloWorldScript = """
    //         println("Hello, World!")
    //     """.trimIndent()
    //     val compilationConfiguration = KiteCompilationConfiguration.with {
    //         displayName("primer")
    //     }
    //     val evaluationConfiguration = KiteEvaluationConfiguration.with {
    //         jvm {
    //             baseClassLoader(Kite::class.java.classLoader)
    //         }
    //     }
    //     BasicJvmScriptingHost().eval(helloWorldScript.toScriptSource(), compilationConfiguration, evaluationConfiguration)
    // }



    /**
     * Compiles script backed by the specified [ScriptHolder] instance and returns the result.
     * In case compilation fails, a `null` is returned instead.
     */
    private suspend fun compileScriptAsync(holder: ScriptHolder): ScriptContext? = withContext(Dispatchers.IO) {
        // Creating a new instance of ScriptContext. Name of the script either file name with no extensions or script's folder name.
        val script = ScriptContext(holder.name, holder.entryPoint)
        // Creating compilation and evaluation configurations from Kite templates.
        val compilationConfiguration = KiteCompilationConfiguration.with {
            displayName(script.name)
        }
        // Getting the cache file and its last modified date before compilation. Used in a later step for determining whether cache was used or not.
        val cacheFile = Kite.Structure.CACHE_DIR.listFiles()?.firstOrNull { it.name.startsWith("${script.name}.") && it.name.endsWith(".cache.jar") }
        val cacheLastModified = cacheFile?.lastModified() ?: 0L
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
        // Invalidating imports cache of the script before compilation.
        // It will then be populated with new entries by KiteCompilationConfiguration.
        Kite.CACHE?.invalidate(holder.name)
        // Compiling/loading the script. Time the operation to verify cache performance.
        val (compiledScript, elapsedTime) = measureTimedValue {
            scriptingHost.eval(holder.entryPoint.toScriptSource(), compilationConfiguration, evaluationConfiguration)
        }
        // Logging diagnostics from a single-thread queue. Otherwise, messages can be displayed in wrong order due to parallel compilation.
        logExecutor.submit {
            if (cacheFile != null && cacheFile.lastModified() == cacheLastModified) {
                logger.infoRich("Compiled <yellow>${holder.name}</yellow> from cache in <yellow>${elapsedTime.inWholeMilliseconds}ms</yellow>.")
            } else {
                logger.infoRich("Compiled <yellow>${holder.name}</yellow> in <yellow>${elapsedTime.inWholeMilliseconds}ms</yellow>.")
            }
            val errorCount = compiledScript.reports.count { it.severity == Severity.FATAL || it.severity == Severity.ERROR || it.severity == Severity.WARNING }
            if (compiledScript is ResultWithDiagnostics.Failure)
                logger.errorRich("Script <yellow>${holder.name}</yellow> reported <yellow>$errorCount</yellow> error(s) during compilation:")
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

    // Compiles and loads specified script file.
    private suspend fun load(holder: ScriptHolder): Boolean = suspendCancellableCoroutine { coroutine ->
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Compiling and loading specified script.
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
