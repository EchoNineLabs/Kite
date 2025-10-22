package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import dev.echonine.kite.extensions.syncCommands
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener


class ScriptContext(private val scriptName: String) {
    private val onLoadCBs = mutableListOf<() -> Unit>()
    private val onUnloadCBs = mutableListOf<() -> Unit>()
    private val commands = mutableListOf<KiteCommand>()
    val eventListeners = mutableListOf<Listener>()
    val name: String
        get() = scriptName

    fun onLoad(cb: () -> Unit) {
        onLoadCBs.add(cb)
    }

    fun runOnLoad() {
        onLoadCBs.forEach { it() }
    }

    fun onUnload(cb: () -> Unit) {
        onUnloadCBs.add(cb)
    }

    fun runOnUnload() {
        onUnloadCBs.forEach { it() }
    }

    internal fun cleanup() {
        // Unregister event listeners
        val pluginManager = Kite.instance?.server?.pluginManager
        eventListeners.forEach { listener ->
            HandlerList.unregisterAll(listener)
        }
        eventListeners.clear()

        // Unregister commands
        val commandMap = Kite.instance?.server?.commandMap
        commands.forEach { command ->
            commandMap?.getCommand(command.name)?.unregister(commandMap)
        }
        Kite.instance?.server?.syncCommands()
        commands.clear()

        onLoadCBs.clear()
        onUnloadCBs.clear()
    }

    inline fun <reified T : Event> on(
        priority: EventPriority = EventPriority.NORMAL,
        noinline handler: (T) -> Unit
    ) {
        val listener = object : Listener {}
        Kite.instance?.server?.pluginManager?.registerEvent(T::class.java, listener, priority, { _, event ->
            if (event is T) {
                handler(event)
            }
        }, Kite.instance!!)
        this.eventListeners.add(listener)
    }

    fun command(name: String, builder: CommandBuilder.() -> Unit) {
        val cmdData = CommandBuilder(name).apply(builder)
        val cmd = KiteCommand(cmdData)
        commands.add(cmd)
        Kite.instance?.server?.commandMap?.register("kite-script", cmd)
        Kite.instance?.server?.syncCommands()
    }

}