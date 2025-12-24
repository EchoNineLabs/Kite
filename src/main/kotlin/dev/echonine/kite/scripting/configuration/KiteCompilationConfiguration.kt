package dev.echonine.kite.scripting.configuration

import dev.echonine.kite.Kite
import dev.echonine.kite.api.annotations.Import
import dev.echonine.kite.scripting.configuration.compat.DynamicServerJarCompat
import dev.echonine.kite.scripting.Script
import dev.echonine.kite.scripting.ScriptContext
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.security.MessageDigest
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache

val updatedClasspath by lazy {
    val classpath = mutableListOf<File>()

    // Resolves all plugins' classpaths in order to make compiler recognize APIs of external plugins.
    Kite.instance?.server?.pluginManager?.plugins?.flatMap {
        classpathFromClassloader(it.javaClass.classLoader) ?: emptyList()
    }?.let { pluginClasspath ->
        classpath.addAll(pluginClasspath)
    }

    // Check if dynamic server JAR compatibility mode is enabled
    if (DynamicServerJarCompat.isEnabled()) {
        // Find the Paper API JAR using enhanced discovery for dynamic server architectures
        DynamicServerJarCompat.findServerJar()?.let { paperApiJar ->
            classpath.add(paperApiJar)
        }
    }

    classpath.distinct()
}

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object KiteCompilationConfiguration : ScriptCompilationConfiguration({
    // Adding annotations to default imports.
    defaultImports(Import::class)
    // Adding Bukkit APIs and Kite to default imports.
    defaultImports.append(PAPER_IMPORTS)
    defaultImports.append(ADVENTURE_IMPORTS)
    defaultImports.append(MINIMESSAGE_IMPORTS)
    defaultImports.append(
        "dev.echonine.kite.api.*",
        "dev.echonine.kite.api.annotations.*",
    )
    // Specifying the base class. For some reason @KotlinScript is not picked up automatically.
    baseClass(KotlinType(Script::class))
    implicitReceivers(ScriptContext::class)

    jvm {
        updateClasspath(updatedClasspath)
        dependenciesFromClassloader(wholeClasspath = true, unpackJarCollections = true)
        dependenciesFromClassContext(Kite::class, wholeClasspath = true)
        compilerOptions("-jvm-target", "21", "-Xcontext-parameters")
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
            if (cacheDirectory.isDirectory || cacheDirectory.mkdirs()) {
                // Configuring compilation cache.
                compilationCache(CompiledScriptJarsCache { script, compilationConfiguration ->
                    // Getting the MD5 checksum and including it in the file name.
                    // MD5 checksum acts as a file identifier here.
                    val mainScriptHash = script.text.toByteArray().let {
                        val md = MessageDigest.getInstance("MD5")
                        md.update(it)
                        md.digest().joinToString("") { byte -> "%02x".format(byte) }
                    }
                    // Also hash the imported scripts
                    val importsHash = (compilationConfiguration[importScripts]
                        ?: emptyList<FileScriptSource>()).joinToString("") { importedScript ->
                        importedScript.text.toByteArray().let {
                            val md = MessageDigest.getInstance("MD5")
                            md.update(it)
                            md.digest().joinToString("") { byte -> "%02x".format(byte) }
                        }
                    }

                    // Creating the final hash by combining main script hash and imports hash
                    val hash = MessageDigest.getInstance("MD5").apply {
                        update(mainScriptHash.toByteArray())
                        update(importsHash.toByteArray())
                    }.digest().joinToString("") { byte -> "%02x".format(byte) }
                    
                    val cacheFileName = "${compilationConfiguration[displayName]}.${hash}.cache.jar"
                    
                    // Purging old cache files with different hashes (not the current one).
                    cacheDirectory.listFiles()
                        ?.filter {
                            it.name.endsWith(".cache.jar") && 
                            it.name.split(".").first() == compilationConfiguration[displayName] &&
                            it.name != cacheFileName
                        }
                        ?.forEach { it.delete() }
                    
                    return@CompiledScriptJarsCache File(cacheDirectory, cacheFileName)
                })
            }
        }
    })

})

val PAPER_IMPORTS = listOf(
    // "co.aikar.timings.*", (Deprecated)
    // "co.aikar.util.*", (Deprecated)
    "com.destroystokyo.paper.*",
    "com.destroystokyo.paper.block.*",
    // "com.destroystokyo.paper.brigadier.*", (Deprecated)
    "com.destroystokyo.paper.entity.*",
    "com.destroystokyo.paper.entity.ai.*",
    "com.destroystokyo.paper.entity.villager.*",
    "com.destroystokyo.paper.event.block.*",
    "com.destroystokyo.paper.event.brigadier.*",
    "com.destroystokyo.paper.event.entity.*",
    "com.destroystokyo.paper.event.inventory.*",
    "com.destroystokyo.paper.event.player.*",
    "com.destroystokyo.paper.event.profile.*",
    "com.destroystokyo.paper.event.server.*",
    "com.destroystokyo.paper.exception.*",
    "com.destroystokyo.paper.inventory.meta.*",
    "com.destroystokyo.paper.loottable.*",
    "com.destroystokyo.paper.network.*",
    "com.destroystokyo.paper.profile.*",
    "com.destroystokyo.paper.util.*",
    "com.destroystokyo.paper.utils.*",
    "io.papermc.paper.*",
    "io.papermc.paper.advancement.*",
    // "io.papermc.paper.annotation.*", (Internal)
    "io.papermc.paper.ban.*",
    "io.papermc.paper.block.*",
    "io.papermc.paper.block.fluid.*",
    "io.papermc.paper.block.fluid.type.*",
    "io.papermc.paper.brigadier.*",
    "io.papermc.paper.chat.*",
    "io.papermc.paper.command.*",
    "io.papermc.paper.command.brigadier.*",
    "io.papermc.paper.command.brigadier.argument.*",
    "io.papermc.paper.command.brigadier.argument.position.*",
    "io.papermc.paper.command.brigadier.argument.predicate.*",
    "io.papermc.paper.command.brigadier.argument.range.*",
    "io.papermc.paper.command.brigadier.argument.resolvers.*",
    "io.papermc.paper.command.brigadier.argument.resolvers.selector.*",
    "io.papermc.paper.configuration.*",
    "io.papermc.paper.connection.*",
    "io.papermc.paper.datacomponent.*",
    "io.papermc.paper.datacomponent.item.*",
    "io.papermc.paper.datacomponent.item.attribute.*",
    "io.papermc.paper.datacomponent.item.blocksattacks.*",
    "io.papermc.paper.datacomponent.item.consumable.*",
    // "io.papermc.paper.datapack.*",
    "io.papermc.paper.dialog.*",
    "io.papermc.paper.enchantments.*",
    "io.papermc.paper.entity.*",
    "io.papermc.paper.event.block.*",
    "io.papermc.paper.event.connection.*",
    "io.papermc.paper.event.connection.configuration.*",
    "io.papermc.paper.event.entity.*",
    "io.papermc.paper.event.executor.*",
    "io.papermc.paper.event.packet.*",
    "io.papermc.paper.event.player.*",
    "io.papermc.paper.event.server.*",
    "io.papermc.paper.event.world.*",
    "io.papermc.paper.event.world.border.*",
    "io.papermc.paper.inventory.*",
    "io.papermc.paper.inventory.tooltip.*",
    "io.papermc.paper.item.*",
    "io.papermc.paper.math.*",
    "io.papermc.paper.persistence.*",
    "io.papermc.paper.plugin.*",
    // "io.papermc.paper.plugin.bootstrap.*",
    "io.papermc.paper.plugin.configuration.*",
    // "io.papermc.paper.plugin.lifecycle.event.*",
    // "io.papermc.paper.plugin.lifecycle.event.handler.*",
    // "io.papermc.paper.plugin.lifecycle.event.handler.configuration.*",
    // "io.papermc.paper.plugin.lifecycle.event.registrar.*",
    // "io.papermc.paper.plugin.lifecycle.event.types.*",
    // "io.papermc.paper.plugin.loader.*",
    // "io.papermc.paper.plugin.loader.library.*",
    // "io.papermc.paper.plugin.loader.library.impl.*",
    // "io.papermc.paper.plugin.provider.classloader.*",
    // "io.papermc.paper.plugin.provider.entrypoint.*",
    // "io.papermc.paper.plugin.provider.util.*",
    "io.papermc.paper.potion.*",
    "io.papermc.paper.raytracing.*",
    "io.papermc.paper.registry.*",
    "io.papermc.paper.registry.data.*",
    "io.papermc.paper.registry.data.client.*",
    "io.papermc.paper.registry.data.dialog.*",
    "io.papermc.paper.registry.data.dialog.action.*",
    "io.papermc.paper.registry.data.dialog.body.*",
    "io.papermc.paper.registry.data.dialog.input.*",
    "io.papermc.paper.registry.data.dialog.type.*",
    "io.papermc.paper.registry.event.*",
    "io.papermc.paper.registry.event.type.*",
    "io.papermc.paper.registry.holder.*",
    "io.papermc.paper.registry.keys.*",
    "io.papermc.paper.registry.keys.tags.*",
    "io.papermc.paper.registry.set.*",
    "io.papermc.paper.registry.tag.*",
    "io.papermc.paper.scoreboard.numbers.*",
    "io.papermc.paper.tag.*",
    "io.papermc.paper.text.*",
    "io.papermc.paper.threadedregions.*",
    "io.papermc.paper.threadedregions.scheduler.*",
    "io.papermc.paper.util.*",
    "io.papermc.paper.world.*",
    "io.papermc.paper.world.damagesource.*",
    "io.papermc.paper.world.flag.*",
    "org.bukkit.*",
    "org.bukkit.advancement.*",
    "org.bukkit.attribute.*",
    "org.bukkit.ban.*",
    "org.bukkit.block.*",
    "org.bukkit.block.banner.*",
    "org.bukkit.block.data.*",
    "org.bukkit.block.data.type.*",
    "org.bukkit.block.sign.*",
    "org.bukkit.block.spawner.*",
    "org.bukkit.block.structure.*",
    "org.bukkit.boss.*",
    "org.bukkit.command.*",
    "org.bukkit.command.defaults.*",
    "org.bukkit.configuration.*",
    "org.bukkit.configuration.file.*",
    "org.bukkit.configuration.serialization.*",
    // "org.bukkit.conversations.*", (Deprecated)
    "org.bukkit.damage.*",
    "org.bukkit.enchantments.*",
    "org.bukkit.entity.*",
    "org.bukkit.entity.boat.*",
    "org.bukkit.entity.memory.*",
    "org.bukkit.entity.minecart.*",
    "org.bukkit.event.*",
    "org.bukkit.event.block.*",
    "org.bukkit.event.command.*",
    "org.bukkit.event.enchantment.*",
    "org.bukkit.event.entity.*",
    "org.bukkit.event.hanging.*",
    "org.bukkit.event.inventory.*",
    "org.bukkit.event.player.*",
    "org.bukkit.event.raid.*",
    "org.bukkit.event.server.*",
    "org.bukkit.event.vehicle.*",
    "org.bukkit.event.weather.*",
    "org.bukkit.event.world.*",
    // "org.bukkit.generator.*",
    "org.bukkit.generator.structure.*",
    // "org.bukkit.help.*",
    "org.bukkit.inventory.*",
    "org.bukkit.inventory.meta.*",
    "org.bukkit.inventory.meta.components.*",
    "org.bukkit.inventory.meta.tags.*",
    "org.bukkit.inventory.meta.trim.*",
    "org.bukkit.inventory.recipe.*",
    "org.bukkit.inventory.view.*",
    "org.bukkit.inventory.view.builder.*",
    "org.bukkit.loot.*",
    "org.bukkit.map.*",
    "org.bukkit.material.*",
    "org.bukkit.material.types.*",
    "org.bukkit.metadata.*",
    "org.bukkit.packs.*",
    "org.bukkit.permissions.*",
    "org.bukkit.persistence.*",
    "org.bukkit.plugin.*",
    "org.bukkit.plugin.java.*",
    "org.bukkit.plugin.messaging.*",
    "org.bukkit.potion.*",
    "org.bukkit.profile.*",
    "org.bukkit.projectiles.*",
    "org.bukkit.scheduler.*",
    "org.bukkit.scoreboard.*",
    "org.bukkit.spawner.*",
    "org.bukkit.structure.*",
    "org.bukkit.tag.*",
    "org.bukkit.util.*",
    "org.bukkit.util.io.*",
    "org.bukkit.util.noise.*",
    "org.bukkit.util.permissions.*",
    // "org.spigotmc.*",
    // "org.spigotmc.event.player" (Deprecated)
)

val ADVENTURE_IMPORTS = listOf(
    // adventure-api
    "net.kyori.adventure.*",
    "net.kyori.adventure.audience.*",
    "net.kyori.adventure.bossbar.*",
    "net.kyori.adventure.builder.*",
    "net.kyori.adventure.chat.*",
    "net.kyori.adventure.dialog.*",
    "net.kyori.adventure.identity.*",
    // "net.kyori.adventure.internal", (Internal)
    // "net.kyori.adventure.internal.properties", (Internal)
    "net.kyori.adventure.inventory.*",
    "net.kyori.adventure.nbt.api.*",
    "net.kyori.adventure.permission.*",
    "net.kyori.adventure.pointer.*",
    "net.kyori.adventure.resource.*",
    "net.kyori.adventure.sound.*",
    "net.kyori.adventure.text.*",
    "net.kyori.adventure.text.event.*",
    "net.kyori.adventure.text.flattener.*",
    "net.kyori.adventure.text.format.*",
    "net.kyori.adventure.text.object",
    "net.kyori.adventure.text.renderer.*",
    "net.kyori.adventure.text.serializer.*",
    "net.kyori.adventure.title.*",
    "net.kyori.adventure.translation.*",
    "net.kyori.adventure.util.*",
    // adventure-key
    "net.kyori.adventure.key.*"

)

val MINIMESSAGE_IMPORTS = listOf(
    "net.kyori.adventure.text.minimessage.MiniMessage",
)