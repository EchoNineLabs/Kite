package dev.echonine.kite

import dev.echonine.kite.commands.KiteCommands
import dev.echonine.kite.scripting.ScriptManager
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class Kite : JavaPlugin() {

    internal lateinit var scriptManager: ScriptManager

    companion object {
        var instance: Kite? = null
            private set
    }

    override fun onEnable() {
        instance = this
        // Initializing ScriptManager and loading all scripts.
        this.scriptManager = ScriptManager(this)
        this.scriptManager.loadAll()
        // Registering
        this.server.commandMap.register("kite", KiteCommands(this))
    }

}