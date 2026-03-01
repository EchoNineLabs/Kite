package dev.echonine.kite.scripting.configuration

import dev.echonine.kite.Kite
import dev.echonine.kite.api.annotations.Dependency
import dev.echonine.kite.api.annotations.Import
import dev.echonine.kite.api.annotations.Relocation
import dev.echonine.kite.api.annotations.Repository
import dev.echonine.kite.scripting.configuration.compat.DynamicServerJarCompat
import dev.echonine.kite.scripting.Script
import dev.echonine.kite.scripting.ScriptContext
import dev.echonine.kite.scripting.ScriptHolder
import dev.echonine.kite.scripting.cache.ImportsCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.zapper.DependencyManager
import revxrsal.zapper.classloader.URLClassLoaderWrapper
import java.io.File
import java.net.URLClassLoader
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
    // Resolves all plugins' classpaths to make the compiler recognize APIs of external plugins.
    Kite.INSTANCE?.server?.pluginManager?.plugins?.flatMap {
        classpathFromClassloader(it.javaClass.classLoader) ?: emptyList()
    }?.let { pluginClasspath ->
        classpath.addAll(pluginClasspath)
    }

    // Resolve all local libraries' classpaths.
    Kite.Structure.LIBS_DIR.mkdirs()
    Kite.Structure.LIBS_DIR
        .listFiles { it.name.endsWith(".jar") }
        ?.let { classpath.addAll(it) }

    // Checking if dynamic server JAR compatibility mode is enabled.
    if (DynamicServerJarCompat.isEnabled()) {
        // Finding the Paper API JAR using and adding to the classpath.
        DynamicServerJarCompat.findServerJar()?.let { paperApiJar ->
            classpath.add(paperApiJar)
        }
    }
    // Removing duplicated entries and returning the list.
    return@lazy classpath.distinct()
}

val importsCache = ImportsCache()

// Mutex *should* potentially solve concurrency issues when two scripts are set to load the *same* dependency at once.
// This can be a side effect of parallel compilation.
val zapperMutex = Mutex()

// var hasCompilationOccurred = false

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object KiteCompilationConfiguration : ScriptCompilationConfiguration({
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
        // Currently, due to how each script can trigger 'refineConfiguration' in parallel, this log can appear a few times.
        // Will be disabled for the time being and re-enabled once parallel loading is either removed or fixed.
        // beforeParsing { context ->
        //     if (!hasCompilationOccurred) {
        //         Kite.instance?.logger?.warning("Initializing Kotlin parser and compiler for the first time. This can take a few seconds...")
        //         hasCompilationOccurred = true
        //     }
        //     return@beforeParsing context.compilationConfiguration.asSuccess()
        // }
        onAnnotations(Import::class, Dependency::class, Repository::class, Relocation::class, handler = { context ->
            // Skipping Kite annotation processor if running outside of server context.
            // At this time, these annotations cannot be easily instructed to work inside IDEA due to technical limitations.
            if (!Kite.Environment.IS_SERVER_AVAILABLE)
                return@onAnnotations context.compilationConfiguration.asSuccess()
            // Collecting all defined annotations.
            val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)
                ?.map { it.annotation }?.takeIf { it.isNotEmpty() }
                // Returning if no annotations were specified.
                ?: return@onAnnotations context.compilationConfiguration.asSuccess()
            val scriptBaseDir = (context.script as? FileBasedScriptSource)?.file?.parentFile
            val importedSources: MutableList<FileScriptSource> = mutableListOf()
            // We don't want to share the instance of DependencyManager between scripts / compiler runs as it can easily store up on stale repositories and dependencies.
            val dependencyManager = DependencyManager(Kite.Structure.DEPS_DIR, URLClassLoaderWrapper.wrap(Kite::class.java.classLoader as URLClassLoader))
            // List of dependencies; for later use when appending them to the compilation config.
            val scriptDependencies: MutableList<String> = mutableListOf()
            // Parsing script annotations.
            annotations.forEach { annotation -> when (annotation) {
                // Adding repositories declared via @Repository.
                is Repository -> {
                    dependencyManager.repository(revxrsal.zapper.repository.Repository.maven(annotation.repository))
                }
                // Adding dependencies declared via @Dependency.
                is Dependency -> {
                    if (annotation.dependency.count { it == ':' } in 2 .. 3) {
                        dependencyManager.dependency(annotation.dependency)
                        // Deconstructing Maven coordinates to variables for ease of use.
                        val (group, artifact, version) = annotation.dependency.split(":")
                        // Adding to the list of script dependencies for later use.
                        scriptDependencies.add("$group.$artifact-$version.jar")
                        // No easy way to figure out what has and what has been not been relocated, so we have to add both and then filter based on which file exists and which one doesn't.
                        scriptDependencies.add("$group.$artifact-$version-relocated.jar")
                    } else if (annotation.dependency.endsWith(".jar")) {
                        scriptDependencies.add(annotation.dependency)
                    }
                }
                // Configuring relocations specified via @Relocation.
                is Relocation -> {
                    dependencyManager.relocate(revxrsal.zapper.relocation.Relocation(annotation.pattern, annotation.newPattern))
                }
                // Collecting other .kite.kts files referenced via @Import.
                is Import -> {
                    annotation.paths.forEach { path ->
                        importedSources += FileScriptSource(scriptBaseDir?.resolve(path)?.canonicalFile ?: File(path))
                    }
                }
            }}
            return@onAnnotations ScriptCompilationConfiguration(context.compilationConfiguration) {
                // Mutex *should* potentially solve concurrency issues when two scripts are set to load the *same* dependency at once.
                // This can be a side effect of parallel compilation.
                runBlocking {
                    zapperMutex.withLock {
                        dependencyManager.load()
                    }
                }
                // Adding downloaded libraries as dependencies.
                dependencies.append(JvmDependency(scriptDependencies.map { File(Kite.Structure.DEPS_DIR, it) }.filter { it.exists() }))
                // Appending imported sources to the script.
                importedSources.takeUnless { it.isEmpty() }?.let { importScripts.append(it) }
            }.asSuccess()
        })

        beforeCompiling { context ->
            return@beforeCompiling ScriptCompilationConfiguration(context.compilationConfiguration) {
                val name = context.compilationConfiguration[ScriptCompilationConfiguration.displayName]!!
                // Getting all imported scripts added to the configuration via annotation processor.
                val imports = context.compilationConfiguration[ScriptCompilationConfiguration.importScripts]
                // Appending to the imports cache. Must be launched in a coroutine since ImportsCache#write is a suspend function backed by Mutex.
                runBlocking {
                    // Writing non-empty imported script paths to the imports cache.
                    imports?.mapNotNull { (it as? FileScriptSource)?.file?.path }?.also {
                        importsCache.append(name, it)
                    }
                }
            }.asSuccess()
        }

    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    hostConfiguration(ScriptingHostConfiguration {
        jvm {
            // Configuring compilation cache.
            compilationCache(CompiledScriptJarsCache { script, compilationConfiguration ->
                // Creating cache directory in case it does not exist.
                Kite.Structure.CACHE_DIR.mkdirs()
                // Creating directories in case they don't exist yet.
                val name = compilationConfiguration[displayName]
                val checksum = MessageDigest.getInstance("MD5")
                // Getting the MD5 checksum and including it in the file name.
                // MD5 checksum acts as a file identifier here.
                checksum.update(script.text.toByteArray())
                // Updating digest with all imported scripts.
                importsCache.cache[name]?.map { File(it) }?.filter { it.exists() }?.forEach {
                    checksum.update(it.readBytes())
                }
                // Converting checksum to a human-readable format so it can be included in the cache file name.
                val hash = checksum.digest().joinToString("") { "%02x".format(it) }
                val cacheFileName = "$name.$hash.cache.jar"
                // Purging old cache files with different hashes (not the current one).
                Kite.Structure.CACHE_DIR.listFiles()
                    ?.filter { it.name.endsWith(".cache.jar") && it.name.split(".").first() == name && it.name != cacheFileName }
                    ?.forEach { it.delete() }
                return@CompiledScriptJarsCache Kite.Structure.CACHE_DIR.resolve(cacheFileName)
            })
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