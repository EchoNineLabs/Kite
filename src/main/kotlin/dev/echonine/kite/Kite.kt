package dev.echonine.kite

import dev.echonine.kite.commands.KiteCommands
import dev.echonine.kite.scripting.ScriptManager
import dev.echonine.kite.scripting.cache.ImportsCache
import dev.faststats.bukkit.BukkitMetrics
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

@Suppress("unused")
class Kite : JavaPlugin() {

    internal lateinit var scriptManager: ScriptManager
    internal lateinit var bStats: Metrics
    internal lateinit var fastStats: dev.faststats.core.Metrics

    companion object {
        var INSTANCE: Kite? = null
            private set
        // Global instance of ImportsCache for ease of access.
        var CACHE: ImportsCache? = null
    }

    object Structure {
        // This property includes a fallback to not throw in case it was accessed in non-server environment.
        // E.g., when Kite's running inside IDEA.
        val KITE_DIR: File
            get() = INSTANCE?.dataFolder ?: File(System.getProperty("user.dir"))
        val SCRIPTS_DIR: File
            get() = KITE_DIR.resolve("scripts")
        val CACHE_DIR: File
            get() = KITE_DIR.resolve("cache")
        val DEPS_DIR: File
            get() = KITE_DIR.resolve("dependencies")
        val LIBS_DIR: File
            get() = KITE_DIR.resolve("libs")
    }

    object Environment {
        val IS_SERVER_AVAILABLE by lazy {
            try {
                return@lazy Class.forName("org.bukkit.Server") != null
            } catch (e: ClassNotFoundException) {
                return@lazy false
            }
        }
    }

    override fun onEnable() {
        INSTANCE = this
        // Initializing ImportsCache.
        CACHE = ImportsCache()
        // Initializing ScriptManager and loading all scripts.
        this.scriptManager = ScriptManager(this)
        this.scriptManager.loadAll()
        // Registering command(s).
        this.server.commandMap.register("kite", KiteCommands(this))
        // Setting up bStats metrics.
        this.bStats = Metrics(this, 27748)
        // Setting up FastStats metrics.
        this.fastStats = BukkitMetrics.factory().token("07d3945a3186e2496be316aaf948c24b").create(this);
    }

    override fun onDisable() {
        // Shutting down bStats SDK.
        bStats.shutdown()
        // Shutting down FastStats SDK.
        fastStats.shutdown()
    }

}