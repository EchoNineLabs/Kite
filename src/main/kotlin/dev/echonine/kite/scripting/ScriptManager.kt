package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import dev.echonine.kite.extensions.errorRich
import dev.echonine.kite.extensions.infoRich
import dev.echonine.kite.extensions.toRich
import dev.echonine.kite.extensions.warnRich
import dev.echonine.kite.extensions.withoutExtensions
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

internal class ScriptManager {
    val logger = ComponentLogger.logger("Kite-ScriptManager")
    private val loadedScripts = mutableMapOf<String, ScriptContext>()
    fun loadAll() {
        val scriptFiles = gatherAvailableScriptFiles()
        logger.infoRich("<green>Found</green> <white>${scriptFiles.size}</white> <green>script(s) to load.</green>")
        for (file in scriptFiles) {
            load(file)
        }
    }

    fun load(file: File) {
        logger.infoRich("<green>Compiling script:</green> <white>${file.path}</white>")
        val scriptName = file.name.withoutExtensions()
        val scriptDefinition = KiteCompilationConfiguration.with {
            displayName(scriptName)
        }
        val script = ScriptContext(scriptName)
        val evalEnv = KiteEvaluationConfiguration.with {
            jvm {
                baseClassLoader(Kite.instance?.javaClass?.classLoader ?: ClassLoader.getSystemClassLoader())
            }
            enableScriptsInstancesSharing()
            implicitReceivers(script)
        }

        val compiledScript = BasicJvmScriptingHost().eval(
            file.toScriptSource(),
            scriptDefinition,
            evalEnv
        )
        compiledScript.reports.forEach {
            when (it.severity) {
                ScriptDiagnostic.Severity.INFO -> logger.infoRich("[${file.name}] ${it.location?.toRich()}: ${it.message}")
                ScriptDiagnostic.Severity.WARNING -> logger.warnRich("[${file.name}] ${it.location?.toRich()}: ${it.message}")
                ScriptDiagnostic.Severity.ERROR -> logger.errorRich("[${file.name}] ${it.location?.toRich()}: ${it.message}")
                ScriptDiagnostic.Severity.FATAL -> logger.errorRich("[${file.name}] ${it.location?.toRich()}: ${it.message}")
                else -> {
                    // Ignore other severities
                }
            }
        }
        logger.infoRich("<green>Finished compiling script:</green> <white>${file.path}</white>")
        loadedScripts[scriptName] = script
        script.runOnLoad()
        logger.infoRich("<green>Successfully loaded script:</green> <white>${file.path}</white>")
    }

    fun load(name: String): Boolean {
        val scriptFiles = gatherAvailableScriptFiles()
        val scriptFile = scriptFiles.find { it.name.withoutExtensions() == name }
        if (scriptFile != null) {
            load(scriptFile)
            return true
        }
        return false
    }

    fun unload(scriptName: String) {
        val script = loadedScripts[scriptName]
        if (script != null) {
            unload(script)
        } else {
            logger.warnRich("<yellow>Attempted to unload script that is not loaded:</yellow> <white>$scriptName</white>")
        }
    }

    fun unload(script: ScriptContext) {
        logger.infoRich("<green>Unloading script:</green> <white>${script.name}</white>")
        script.runOnUnload()
        script.cleanup()
        loadedScripts.remove(script.name)
        logger.infoRich("<green>Successfully unloaded script:</green> <white>${script.name}</white>")
    }

    fun getLoadedScripts(): Map<String, ScriptContext> {
        return loadedScripts.toMap()
    }

    fun gatherAvailableScriptFiles(): List<File> {
        val scriptsFolder = Kite.instance?.dataFolder?.resolve("scripts")

        // Gather all .kite.kts files in the scripts folder or subfolders with a main.kite.kts file
        val scriptFiles = mutableListOf<File>()
        if (scriptsFolder != null && scriptsFolder.exists() && scriptsFolder.isDirectory) {
            scriptsFolder.walk().forEach { file ->
                if (file.isFile && file.extension == "kts" && file.name.endsWith(".kite.kts")) {
                    val scriptName = file.name.withoutExtensions()
                    if (!loadedScripts.containsKey(scriptName)) {
                        scriptFiles.add(file)
                    }
                } else if (file.isDirectory) {
                    val mainScriptFile = File(file, "main.kite.kts")
                    if (mainScriptFile.exists() && mainScriptFile.isFile) {
                        val scriptName = mainScriptFile.name.withoutExtensions()
                        if (!loadedScripts.containsKey(scriptName)) {
                            scriptFiles.add(mainScriptFile)
                        }
                    }
                }
            }
        } else {
            scriptsFolder?.mkdirs()
        }
        return scriptFiles
    }
}