package dev.echonine.kite.scripting.configuration

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.Import
import dev.echonine.kite.scripting.Script
import dev.echonine.kite.scripting.ScriptContext
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader

// Resolves all plugins' classpaths in order to make compiler recognize APIs of external plugins.
val updatedClasspath by lazy {
    Kite.instance?.server?.pluginManager?.plugins?.flatMap {
        classpathFromClassloader(it.javaClass.classLoader) ?: emptyList()
    }
}

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object KiteCompilationConfiguration : ScriptCompilationConfiguration({
    // Adding annotations to default imports.
    defaultImports(Import::class)
    // Adding Bukkit APIs and Kite to default imports.
    defaultImports.append(
        // Bukkit (More will be specified later)
        "org.bukkit.*",
        // Kite
        "dev.echonine.kite.extensions.*",
    )
    // Specifying the base class. For some reason @KotlinScript is not picked up automatically.
    baseClass(KotlinType(Script::class))
    implicitReceivers(ScriptContext::class)

    jvm {
        updateClasspath(updatedClasspath)
        dependenciesFromClassContext(Kite::class, wholeClasspath = true)
    }

    providedProperties(
        "plugin" to JavaPlugin::class,
        "server" to Server::class
    )

    refineConfiguration {
        onAnnotations(Import::class, handler = { context ->
            // Collecting all defined annotations. Returning if no annotations were specified.
            val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf {
                it.isNotEmpty()
            } ?: return@onAnnotations context.compilationConfiguration.asSuccess()
            val scriptBaseDir = (context.script as? FileBasedScriptSource)?.file?.parentFile
            val importedSources = annotations.flatMap {
                (it.annotation as? Import)?.paths.orEmpty().map { path ->
                    FileScriptSource(scriptBaseDir?.resolve(path) ?: File(path))
                }
            }
            return@onAnnotations ScriptCompilationConfiguration(context.compilationConfiguration) {
                if (importedSources.isNotEmpty()) {
                    importScripts.append(importedSources)
                }
            }.asSuccess()
        })
    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

//    hostConfiguration(ScriptingHostConfiguration {
//        jvm {
//            val runningLocation = System.getProperty("user.dir") ?: "."
//            val cacheBaseDir = File(runningLocation, "plugins/Kite/cache")
//            //TODO: Swap to Kite plugin data folder. Since this is a scripting module, we don't have direct access to the plugin instance here.
//            if (!cacheBaseDir.exists()) {
//                cacheBaseDir.mkdirs()
//            }
//
//            if (cacheBaseDir.exists() && cacheBaseDir.isDirectory) {
//                compilationCache(
//                    CompiledScriptJarsCache { script, scriptCompilationConfiguration ->
//                        val scriptDisplayName = scriptCompilationConfiguration[displayName] ?: "script"
//                        // get the MD5 hash of the script text to use as part of the file name
//                        val hash = script.text.toByteArray().let {
//                            val md = MessageDigest.getInstance("MD5")
//                            md.update(it)
//                            md.digest().joinToString("") { byte -> "%02x".format(byte) }
//                        }
//                        val fileName = "${scriptDisplayName}-${hash}.jar"
//                        File(cacheBaseDir, fileName)
//                    }
//                )
//            }
//        }
//    })
})

