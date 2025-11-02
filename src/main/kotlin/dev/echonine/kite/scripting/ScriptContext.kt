package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import dev.echonine.kite.extensions.syncCommands
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import java.io.File


class ScriptContext(
    /** Effective name of the script. Either file name with no extensions or script's folder name. */
    val name: String,
    /** File representing the script itself, or 'main.kite.kts' script inside script's folder. */
    val file: File
) {
    private val onLoadCBs = mutableListOf<() -> Unit>()
    private val onUnloadCBs = mutableListOf<() -> Unit>()
    private val commands = mutableListOf<KiteScriptCommand>()
    val eventListeners = mutableListOf<Listener>()
    val bukkitTasks = mutableListOf<Int>()
    val foliaTasks = mutableListOf<ScheduledTask>()

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
        eventListeners.forEach { listener ->
            HandlerList.unregisterAll(listener)
        }
        eventListeners.clear()

        // Unregister commands
        val commandMap = Kite.instance?.server?.commandMap
        commands.forEach { command ->
            command.unregister(commandMap!!)
            commandMap.knownCommands.remove(command.name)
            command.aliases.forEach { alias ->
                commandMap.knownCommands.remove(alias)
            }
        }
        Kite.instance?.server?.syncCommands()
        commands.clear()

        // Cancel scheduled bukkit tasks
        val scheduler = Kite.instance?.server?.scheduler
        bukkitTasks.forEach { taskId ->
            scheduler?.cancelTask(taskId)
        }
        bukkitTasks.clear()

        // Cancel Folia scheduled tasks
        foliaTasks.forEach { task ->
            task.cancel()
        }

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
        val cmd = KiteScriptCommand(cmdData)
        commands.add(cmd)
        Kite.instance?.server?.commandMap?.register("kite-script", cmd)
        Kite.instance?.server?.syncCommands()
    }

}