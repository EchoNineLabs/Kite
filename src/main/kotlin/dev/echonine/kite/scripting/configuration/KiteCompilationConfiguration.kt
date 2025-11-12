package dev.echonine.kite.scripting.configuration

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.Import
import dev.echonine.kite.scripting.Script
import dev.echonine.kite.scripting.ScriptContext
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.security.MessageDigest
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache

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

    hostConfiguration(ScriptingHostConfiguration {
        jvm {
            // Getting the cache directory from plugin's data folder or from configured / default path if running with no server.
            val cacheDirectory = File(Kite.instance?.dataFolder?.path ?: System.getProperty("user.dir", "."), "cache")
            // Creating directories in case they don't exist yet.
            cacheDirectory.mkdirs()
            if (cacheDirectory.isDirectory) {
                // Purging old cache files except the most recent one for each script. Filtering .cache.jar files and grouping by the script name.
                cacheDirectory.listFiles()?.filter { it.name.endsWith(".cache.jar") }?.groupBy { it.name.split(".").dropLast(2) }
                    ?.forEach { (_, files) ->
                        // Skipping scripts with no stale cache files.
                        if (files.size <= 1)
                            return@forEach
                        // Removing all stale cache files.
                        files.maxByOrNull { it.lastModified() }?.let { newestFile ->
                            files.filter { it != newestFile }.forEach { it.delete() }
                        }
                }
                // Configuring compilation cache.
                compilationCache(CompiledScriptJarsCache { script, compilationConfiguration ->
                    // Getting the MD5 checksum and including it in the file name.
                    // MD5 checksum acts as a file identifier here.
                    val hash = script.text.toByteArray().let {
                        val md = MessageDigest.getInstance("MD5")
                        md.update(it)
                        md.digest().joinToString("") { byte -> "%02x".format(byte) }
                    }
                    return@CompiledScriptJarsCache File(cacheDirectory, "${compilationConfiguration[displayName]}.${hash}.cache.jar")
                })
            }
        }
    })
})

