package dev.echonine.kite.api

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.ScriptContext
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit

/**
 * Same as [AsyncScheduler.runNow] but uses Kite instance as plugin and is more Kotlin-friendly.
 *
 * @param task The task to run.
 * @return The [ScheduledTask] that represents the scheduled task.
 */
context(context: ScriptContext)
fun AsyncScheduler.runNow(task: (KiteScheduledTask) -> Unit): ScheduledTask {
    lateinit var kiteTask: KiteScheduledTask
    // Wrapping ScheduledTask as KiteScheduledTask in order to expose the overridden cancel() method.
    // While this technically does not make any effect on 'runNow' - we should still use the wrapper for consistency with other extensions.
    kiteTask = KiteScheduledTask(context, this.runNow(Kite.instance!!, { task(kiteTask) }))
    // Run now tasks are not tracked in ScriptContext as they execute immediately.
    return kiteTask
}

/**
 * Same as [AsyncScheduler.runDelayed] but uses Kite instance as plugin and is more Kotlin-friendly.
 *
 * @param delay The time delay to pass before executing the task.
 * @param unit The time unit for the delay.
 * @param task The task to run.
 * @return The [ScheduledTask] that represents the scheduled task.
 */
context(context: ScriptContext)
fun AsyncScheduler.runDelayed(
    delay: Long,
    unit: TimeUnit,
    task: (KiteScheduledTask) -> Unit
): KiteScheduledTask {
    lateinit var kiteTask: KiteScheduledTask
    // Wrapping ScheduledTask as KiteScheduledTask in order to expose the overridden cancel() method.
    kiteTask = KiteScheduledTask(context, this.runDelayed(
        Kite.instance!!,
        {
            task(kiteTask)
            // Remove the task from the context's list after execution.
            context.foliaTasks.remove(kiteTask)
        },
        delay,
        unit
    ))
    context.foliaTasks.add(kiteTask)
    return kiteTask
}

/**
 * Same as [AsyncScheduler.runAtFixedRate] but uses Kite instance as plugin and is more Kotlin-friendly.
 *
 * @param initialDelay The time delay to pass before the first execution of the task.
 * @param period The time between task executions after the first execution of the task.
 * @param unit The time unit for the initial delay and period.
 * @param task The task to run.
 * @return The [ScheduledTask] that represents the scheduled task.
 */
context(context: ScriptContext)
fun AsyncScheduler.runAtFixedRate(
    initialDelay: Long = 0,
    period: Long,
    unit: TimeUnit,
    task: (KiteScheduledTask) -> Unit
): KiteScheduledTask {
    lateinit var kiteTask: KiteScheduledTask
    // Wrapping ScheduledTask as KiteScheduledTask in order to expose the overridden cancel() method.
    kiteTask = KiteScheduledTask(context, this.runAtFixedRate(
        Kite.instance!!,
        { task(kiteTask) },
        initialDelay,
        period,
        unit
    ))
    context.foliaTasks.add(kiteTask)
    return kiteTask
}