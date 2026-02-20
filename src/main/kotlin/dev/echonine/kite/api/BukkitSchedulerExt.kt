package dev.echonine.kite.api

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.ScriptContext
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask

context(context: ScriptContext)
private fun makeBukkitRunnable(task: (BukkitRunnable) -> Unit, isRepeating: Boolean = false): BukkitRunnable {
    return object : BukkitRunnable() {
        override fun run() {
            task(this)

            // Only remove one-time tasks from tracking after execution
            // Repeating tasks should remain tracked for proper cleanup
            if (!isRepeating && this.taskId in context.bukkitTasks) {
                context.bukkitTasks.remove(this.taskId)
            }
        }

        override fun cancel() {
            super.cancel()
            // Always remove cancelled tasks from tracking
            context.bukkitTasks.remove(this.taskId)
        }
    }
}

/**
 * Same as [BukkitScheduler.runTaskLater] but uses Kite instance as plugin and is more Kotlin-friendly.
 *
 * @param delayTicks The delay in ticks before executing the task.
 * @param task The task to run.
 *
 */
context(context: ScriptContext)
fun BukkitScheduler.runTask(delayTicks: Long = 0, task: (BukkitRunnable) -> Unit): BukkitTask {
    val runnableTask = makeBukkitRunnable(task, isRepeating = false)
    return runnableTask.runTaskLater(Kite.INSTANCE!!, delayTicks).also {
        context.bukkitTasks.add(it.taskId)
    }
}

/**
 * Same as [BukkitScheduler.runTaskLaterAsynchronously] but uses Kite instance as plugin and is more Kotlin-friendly.
 */
context(context: ScriptContext)
fun BukkitScheduler.runTaskAsync(delayTicks: Long = 0, task: (BukkitRunnable) -> Unit): BukkitTask {
    val runnableTask = makeBukkitRunnable(task, isRepeating = false)
    return runnableTask.runTaskLaterAsynchronously(Kite.INSTANCE!!, delayTicks).also {
        context.bukkitTasks.add(it.taskId)
    }
}

/**
 * Same as [BukkitScheduler.runTaskTimer] but uses Kite instance as plugin and is more Kotlin-friendly.
 */
context(context: ScriptContext)
fun BukkitScheduler.runTaskTimer(
    periodTicks: Long,
    delayTicks: Long = 0,
    task: (BukkitRunnable) -> Unit
): BukkitTask {
    val runnableTask = makeBukkitRunnable(task, isRepeating = true)
    return runnableTask.runTaskTimer(Kite.INSTANCE!!, delayTicks, periodTicks).also {
        context.bukkitTasks.add(it.taskId)
    }
}

/**
 * Same as [BukkitScheduler.runTaskTimerAsynchronously] but uses Kite instance as plugin and is more Kotlin-friendly.
 */
context(context: ScriptContext)
fun BukkitScheduler.runTaskTimerAsync(
    periodTicks: Long,
    delayTicks: Long = 0,
    task: (BukkitRunnable) -> Unit
): BukkitTask {
    val runnableTask = makeBukkitRunnable(task, isRepeating = true)
    return runnableTask.runTaskTimerAsynchronously(Kite.INSTANCE!!, delayTicks, periodTicks).also {
        context.bukkitTasks.add(it.taskId)
    }
}