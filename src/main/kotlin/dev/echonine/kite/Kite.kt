package dev.echonine.kite

import dev.echonine.kite.commands.KiteCommands
import dev.echonine.kite.scripting.ScriptManager
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class Kite : JavaPlugin() {

    internal lateinit var scriptManager: ScriptManager
    internal lateinit var metrics: Metrics

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
        // Connecting to bStats.
        this.metrics = Metrics(this, 27748)
    }

    override fun onDisable() {
        // Disconnecting from bStats.
        metrics.shutdown()
    }

}