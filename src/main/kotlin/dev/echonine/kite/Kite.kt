package dev.echonine.kite

import dev.echonine.kite.commands.KiteCommands
import dev.echonine.kite.scripting.ScriptManager
import dev.faststats.bukkit.BukkitMetrics
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class Kite : JavaPlugin() {

    internal lateinit var scriptManager: ScriptManager
    internal lateinit var bStats: Metrics
    internal lateinit var fastStats: dev.faststats.core.Metrics

    companion object {
        var instance: Kite? = null
            private set
    }

    override fun onEnable() {
        instance = this
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