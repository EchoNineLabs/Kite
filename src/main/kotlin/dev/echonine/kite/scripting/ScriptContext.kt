package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import dev.echonine.kite.util.extensions.syncCommands
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import java.io.File
import java.util.concurrent.ConcurrentHashMap


class ScriptContext(
    /** Effective name of the script. Either file name with no extensions or script's folder name. */
    val name: String,
    /** File representing entry point file of the script. It is either the script itself, or main.kite.kts file inside the script's folder. */
    val entryPoint: File
) {
    private val onLoadCBs = mutableListOf<() -> Unit>()
    private val onUnloadCBs = mutableListOf<() -> Unit>()
    private val commands = mutableListOf<KiteScriptCommand>()
    val eventListeners = mutableListOf<Listener>()
    val logger = ComponentLogger.logger("Kite/$name")

    // Because BukkitTask#cancel() method (which essentially removes task from this list) can be called from different threads, this should be a concurrent set.
    val bukkitTasks = ConcurrentHashMap.newKeySet<Int>()

    // Because ScheduledTask#cancel() method (which essentially removes task from this list) can be called from different threads, this should be a concurrent set.
    val foliaTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

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
        Kite.instance?.server?.commandMap?.register(cmd.namespace, cmd)
        Kite.instance?.server?.syncCommands()
    }

}