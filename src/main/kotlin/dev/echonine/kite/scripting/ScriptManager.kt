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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.jetbrains.annotations.Unmodifiable
import java.io.File
import java.util.concurrent.Executors
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

internal class ScriptManager(val plugin: Kite) {

    private val logger = ComponentLogger.logger("Kite")
    private val scriptsFolder = File(plugin.dataFolder, "scripts")
    private val loadedScripts = mutableMapOf<String, ScriptContext>()
    private val logExecutor = Executors.newSingleThreadExecutor()

    // Returns an unmodifiable view of all loaded scripts.
    fun getLoadedScripts(): @Unmodifiable Map<String, ScriptContext> = loadedScripts.toMap()

    // Returns true if specified script is loaded.
    fun isScriptLoaded(name: String): Boolean = loadedScripts.containsKey(name)

    // Collects all available script files to a list and returns it.
    fun gatherAvailableScriptFiles(): List<ScriptHolder> {
        // Creating 'plugins/Kite/scripts/' directory in case it doesn't exist.
        if (!scriptsFolder.exists())
            scriptsFolder.mkdirs()
        // Otherwise, iterating over all files inside scripts directory.
        else if (scriptsFolder.isDirectory) {
            return scriptsFolder.listFiles()
                .mapNotNull { ScriptHolder.fromName(it.nameWithoutExtensions, scriptsFolder) }
                .distinctBy { it.name }
                .toList()
            }
        return emptyList()
    }

    // Compiles and loads all available scripts.
    fun loadAll() {
        val scriptHolders = gatherAvailableScriptFiles()
        logger.infoRich("Found <yellow>${scriptHolders.size} <reset>script(s) to load.")
        // Compiling all available scripts in parallel.
        val compiledScripts = runBlocking {
            scriptHolders.map { holder -> async(Dispatchers.Default) {
                try {
                    compileScriptAsync(holder)
                } catch (e: Throwable) {
                    // Logging error(s) to the console and returning null.
                    logger.errorRich("Script <yellow>${holder.name} <red>reported errors during compilation.")
                    logger.errorRich("  (1) ${e.javaClass.name}: ${e.message}")
                    if (e.cause != null)
                        logger.errorRich("  (2) ${e.cause!!.javaClass.name}: ${e.cause!!.message}")
                    return@async null
                }
            }}.awaitAll().filterNotNull()
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

    // Compiles specified script file and returns the result.
    private suspend fun compileScriptAsync(holder: ScriptHolder): ScriptContext? = withContext(Dispatchers.IO) {
        logger.infoRich("Compiling <yellow>${holder.name}<reset>...")
        // Creating a new instance of ScriptContext. Name of the script either file name with no extensions or script's folder name.
        val script = ScriptContext(holder.name, holder.entryPoint)
        // Creating compilation and evaluation configurations from Kite templates.
        val compilationConfiguration = KiteCompilationConfiguration.with {
            displayName(script.name)
        }
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
        // Evaluating / running compiled script.
        val compiledScript = BasicJvmScriptingHost().eval(holder.entryPoint.toScriptSource(), compilationConfiguration, evaluationConfiguration)
        // Logging diagnostics from a single-thread queue. Otherwise messages can be displayed in wrong order due to parallel compilation.
        logExecutor.submit {
            val errorCount = compiledScript.reports.count { it.severity == Severity.FATAL || it.severity == Severity.ERROR || it.severity == Severity.WARNING }
            if (compiledScript is ResultWithDiagnostics.Failure)
                logger.errorRich("Script <yellow>${holder.name}</yellow> reported <yellow>$errorCount</yellow> error(s) during compilation:")
            compiledScript.reports.forEach {
                val message = "  <yellow>(${it.sourcePath?.substringAfterLast("/")}, line ${it.location?.start?.line ?: "???"}, col ${it.location?.start?.col ?: "???"})</yellow> ${it.message}"
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

    // Compiles and loads specified script. Name must be either script's file name, or name of directory containing main.kite.kts file.
    fun load(name: String): Boolean {
        // Finding script by the specified name. Returning false if not found.
        val holder = ScriptHolder.fromName(name, scriptsFolder) ?: return false
        // Returning false if already loaded.
        if (loadedScripts.containsKey(holder.name))
            return false
        // Loading the script.
        load(holder)
        return true
    }

    // Compiles and loads specified script file.
    private fun load(holder: ScriptHolder): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            try {
                // Compiling and loading specified script.
                compileScriptAsync(holder)?.let { script ->
                    plugin.server.globalRegionScheduler.run(plugin, {
                        loadedScripts[script.name] = script
                        script.runOnLoad()
                        logger.infoRich("Script <yellow>${holder.name}<reset> has been successfully loaded.")
                    })
                }
            } catch (e: Exception) {
                // Logging error(s) to the console and returning null.
                logger.errorRich("Script <yellow>${holder.name} reported errors during compilation.")
                logger.errorRich("  (1) ${e.javaClass.name}: ${e.message}")
                if (e.cause != null)
                    logger.errorRich("  (2) ${e.cause!!.javaClass.name}: ${e.cause!!.message}")
            }
        }
    }

    // Unloads specified script by it's name. Returns false if script isn't loaded.
    fun unload(scriptName: String): Boolean {
        return loadedScripts[scriptName]?.let {
            unload(it)
            return@let true
        } ?: false
    }

    // Unloads specified script by it's context.
    private fun unload(script: ScriptContext) {
        plugin.server.globalRegionScheduler.run(plugin, {
            script.runOnUnload()
            script.cleanup()
            loadedScripts.remove(script.name)
            logger.infoRich("<green>Successfully unloaded script:</green> <white>${script.name}</white>")
        })
    }

}