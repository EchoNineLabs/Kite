package dev.echonine.kite.api

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.ScriptContext
import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

/** Same as [EntityScheduler.run] but uses Kite instance as plugin and is more Kotlin-friendly.
 *
 * @param task The task to run.
 * @param retired The task to run when the entity is retired.
 * @return The [ScheduledTask] that represents the scheduled task.
 */
context(context: ScriptContext)
fun EntityScheduler.run(task: (KiteScheduledTask) -> Unit, retired: () -> Unit = {}): KiteScheduledTask? {
    lateinit var kiteTask: KiteScheduledTask
    // Wrapping ScheduledTask as KiteScheduledTask in order to expose the overridden cancel() method.
    // While this technically does not make any effect on 'run' - we should still use the wrapper for consistency with other extensions.
    kiteTask = KiteScheduledTask(context, this.run(Kite.instance!!, { task(kiteTask) }, retired) ?: return null)
    // Run now tasks are not tracked in ScriptContext as they execute immediately.
    return kiteTask
}

/** Same as [EntityScheduler.runDelayed] but uses Kite instance as plugin and is more Kotlin-friendly.
 *
 * @param delayTicks The time delay to pass before executing the task.
 * @param task The task to run.
 * @param retired The task to run when the entity is retired.
 * @return The [ScheduledTask] that represents the scheduled task.
 */
context(context: ScriptContext)
fun EntityScheduler.runDelayed(
    delayTicks: Long,
    task: (KiteScheduledTask) -> Unit,
    retired: () -> Unit = {}
): KiteScheduledTask? {
    lateinit var kiteTask: KiteScheduledTask
    // Wrapping ScheduledTask as KiteScheduledTask in order to expose the overridden cancel() method.
    kiteTask = KiteScheduledTask(context, this.runDelayed(
        Kite.instance!!,
        {
            task(kiteTask)
            // Remove the task from the context's list after execution.
            context.foliaTasks.remove(kiteTask)
        },
        retired,
        delayTicks,
    ) ?: return null)
    context.foliaTasks.add(kiteTask)
    return kiteTask
}

/** Same as [EntityScheduler.runAtFixedRate] but uses Kite instance as plugin and is more Kotlin-friendly.
 *
 * @param initialDelayTicks The time delay to pass before the first execution of the task.
 * @param periodTicks The time between task executions after the first execution of the task.
 * @param task The task to run.
 * @param retired The task to run when the entity is retired. Default is an empty function.
 * @return The [ScheduledTask] that represents the scheduled task.
 */
context(context: ScriptContext)
fun EntityScheduler.runAtFixedRate(
    initialDelayTicks: Long = 0,
    periodTicks: Long,
    task: (KiteScheduledTask) -> Unit,
    retired: () -> Unit = {}
): KiteScheduledTask? {
    lateinit var kiteTask: KiteScheduledTask
    // Wrapping ScheduledTask as KiteScheduledTask in order to expose the overridden cancel() method.
    kiteTask = KiteScheduledTask(context, this.runAtFixedRate(
        Kite.instance!!,
        { task(kiteTask) },
        retired,
        initialDelayTicks,
        periodTicks
    ) ?: return null)
    context.foliaTasks.add(kiteTask)
    return kiteTask
}