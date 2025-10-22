package dev.echonine.kite

import dev.echonine.kite.commands.KiteCommands
import dev.echonine.kite.scripting.ScriptManager
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class Kite : JavaPlugin() {
    internal val scriptManager: ScriptManager = ScriptManager()
    companion object {
        var instance: Kite? = null
            private set
    }
    override fun onLoad() {
        instance = this
    }
    override fun onEnable() {
        this.server.commandMap.register("kite", KiteCommands())
        this.scriptManager.loadAll()
    }
}