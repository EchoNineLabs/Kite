package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import org.bukkit.Server
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.security.MessageDigest
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache

// Why the fuck is this needed.
// Kotlin scripting seems so fragile
private val pluginClassPath by lazy {
    Kite.instance?.server?.pluginManager?.plugins?.flatMap {
        classpathFromClassloader(it.javaClass.classLoader) ?: emptyList()
    }
}

object KiteCompilationConfiguration : ScriptCompilationConfiguration({
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
        dependenciesFromClassContext(Kite::class, wholeClasspath = true)
        updateClasspath(pluginClassPath)
        compilerOptions(
            "-jvm-target", "21",
        )
    }

    implicitReceivers(ScriptContext::class)

    providedProperties(
        "plugin" to JavaPlugin::class,
        "server" to Server::class
    )

    defaultImports(
    )

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    hostConfiguration(ScriptingHostConfiguration {
        jvm {
            val runningLocation = System.getProperty("user.dir") ?: "."
            val cacheBaseDir = File(runningLocation, "plugins/Kite/cache")
            //TODO: Swap to Kite plugin data folder. Since this is a scripting module, we don't have direct access to the plugin instance here.
            if (!cacheBaseDir.exists()) {
                cacheBaseDir.mkdirs()
            }

            if (cacheBaseDir.exists() && cacheBaseDir.isDirectory) {
                compilationCache(
                    CompiledScriptJarsCache { script, scriptCompilationConfiguration ->
                        val scriptDisplayName = scriptCompilationConfiguration[displayName] ?: "script"
                        // get the MD5 hash of the script text to use as part of the file name
                        val hash = script.text.toByteArray().let {
                            val md = MessageDigest.getInstance("MD5")
                            md.update(it)
                            md.digest().joinToString("") { byte -> "%02x".format(byte) }
                        }
                        val fileName = "${scriptDisplayName}-${hash}.jar"
                        File(cacheBaseDir, fileName)
                    }
                )
            }
        }
    })
})