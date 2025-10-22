package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import kotlinx.coroutines.runBlocking
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.security.MessageDigest
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.Repository
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.dependencies.resolveFromScriptSourceAnnotations
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache

// Why the fuck is this needed.
// Kotlin scripting seems so fragile
private val pluginClassPath by lazy {
    Kite.instance?.server?.pluginManager?.plugins?.flatMap {
        classpathFromClassloader(it.javaClass.classLoader) ?: emptyList()
    }
}

//Imports from: https://github.com/DevSrSouza/Bukkript/blob/08775675ce6ff2601848e7323e1cb35df9b14a44/script-definition/src/main/kotlin/br/com/devsrsouza/bukkript/script/definition/BaseImports.kt#L3-L43
private val bukkitImports = listOf(
    "org.bukkit.*",
    "org.bukkit.block.*",
    "org.bukkit.block.banner.*",
    "org.bukkit.command.*",
    "org.bukkit.configuration.*",
    "org.bukkit.configuration.file.*",
    "org.bukkit.configuration.serialization.*",
    "org.bukkit.enchantments.*",
    "org.bukkit.entity.*",
    "org.bukkit.entity.minecart.*",
    "org.bukkit.event.*",
    "org.bukkit.event.block.*",
    "org.bukkit.event.enchantment.*",
    "org.bukkit.event.entity.*",
    "org.bukkit.event.hanging.*",
    "org.bukkit.event.inventory.*",
    "org.bukkit.event.painting.*",
    "org.bukkit.event.player.*",
    "org.bukkit.event.server.*",
    "org.bukkit.event.weather.*",
    "org.bukkit.event.world.*",
    "org.bukkit.generator.*",
    "org.bukkit.help.*",
    "org.bukkit.inventory.*",
    "org.bukkit.inventory.meta.*",
    "org.bukkit.map.*",
    "org.bukkit.material.*",
    "org.bukkit.metadata.*",
    "org.bukkit.permissions.*",
    "org.bukkit.plugin.*",
    "org.bukkit.plugin.messaging.*",
    "org.bukkit.potion.*",
    "org.bukkit.projectiles.*",
    "org.bukkit.scheduler.*",
    "org.bukkit.scoreboard.*",
    "org.bukkit.util.*",
    "org.bukkit.util.io.*",
    "org.bukkit.util.noise.*",
    "org.bukkit.util.permissions.*",
)

private val scriptImports = listOf(
    "kotlin.script.experimental.dependencies.DependsOn",
    "kotlin.script.experimental.dependencies.Repository",
    "dev.echonine.kite.scripting.Import",
    "dev.echonine.kite.scripting.CompilerOptions",
)

private val imports = bukkitImports + scriptImports

object KiteCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports(imports)

    jvm {
        updateClasspath(pluginClassPath)
        dependenciesFromCurrentContext(wholeClasspath = true)
        dependenciesFromClassContext(Kite::class, wholeClasspath = true)
        dependenciesFromClassContext(KiteCompilationConfiguration::class, wholeClasspath = true)
        compilerOptions(
            "-jvm-target", "21",
        )
    }

    implicitReceivers(ScriptContext::class)

    providedProperties(
        "plugin" to JavaPlugin::class, "server" to Server::class
    )

    refineConfiguration {
        //onAnnotations(DependsOn::class, Repository::class, Import::class, CompilerOptions::class, handler = AnnotationProcessor())
    }

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
                    })
            }
        }
    })
})

