package dev.echonine.kite.extensions

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.ScriptContext
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit


/*
* Same as {@link AsyncScheduler#runNow(org.bukkit.plugin.Plugin, java.util.function.Consumer)} but uses Kite instance as plugin and more Kotlin-friendly.
*
* @param task The task to run.
* @return The {@link ScheduledTask} that represents the scheduled task.
 */
fun AsyncScheduler.runNow(task: (ScheduledTask) -> Unit): ScheduledTask {
    val task = this.runNow(Kite.instance!!, task)
    // Run now tasks are not tracked in ScriptContext as they execute immediately
    return task
}

/*
* Same as {@link AsyncScheduler#runDelayed(org.bukkit.plugin.Plugin, java.util.function.Consumer, long, java.util.concurrent.TimeUnit)} but uses Kite instance as plugin and more Kotlin-friendly.
*
* @param delay The time delay to pass before executing the task.
* @param unit The time unit for the delay.
* @param task The task to run.
* @return The {@link ScheduledTask} that represents the scheduled task.
 */
context(context: ScriptContext)
fun AsyncScheduler.runDelayed(
    delay: Long,
    unit: TimeUnit,
    task: (ScheduledTask) -> Unit
): ScheduledTask {
    val task = this.runDelayed(
        Kite.instance!!,
        {
            task(it)
            context.foliaTasks.remove(it) // Remove the task from the context's list after execution
        },
        delay,
        unit
    )
    context.foliaTasks.add(task)
    return task
}

/*
* Same as {@link AsyncScheduler#runAtFixedRate(org.bukkit.plugin.Plugin, java.util.function.Consumer, long, long, java.util.concurrent.TimeUnit)} but uses Kite instance as plugin and
* more Kotlin-friendly.
*
* @param initalDelay The time delay to pass before the first execution of the task.
* @param period The time between task executions after the first execution of the task.
* @param unit The time unit for the initial delay and period.
* @param task The task to run.
* @return The {@link ScheduledTask} that represents the scheduled task.
 */
context(context: ScriptContext)
fun AsyncScheduler.runAtFixedRate(
    initialDelay: Long = 0,
    period: Long,
    unit: TimeUnit,
    task: (ScheduledTask) -> Unit
): ScheduledTask {
    //TODO: We need to find a way to remove the task from the context's list when it's cancelled.
    val task = this.runAtFixedRate(
        Kite.instance!!,
        task,
        initialDelay,
        period,
        unit
    )
    context.foliaTasks.add(task)
    return task
}