package dev.echonine.kite

import dev.echonine.kite.scripting.ScriptManager
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class Kite : JavaPlugin() {
    private val scriptManager: ScriptManager = ScriptManager()
    companion object {
        var instance: Kite? = null
            private set
    }
    override fun onLoad() {
        instance = this
    }
    override fun onEnable() {
        this.scriptManager.loadAll()
    }
}