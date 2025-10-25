package dev.echonine.kite.extensions

import dev.echonine.kite.Kite
import dev.echonine.kite.scripting.ScriptContext
import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

/*
*
* @param task The task to run.
* @param retired The task to run when the entity is retired.
* @return The {@link ScheduledTask} that represents the scheduled task.
 */
context(context: ScriptContext)
fun EntityScheduler.runNow(task: (ScheduledTask) -> Unit, retired: () -> Unit = {}): ScheduledTask? {
    val task = this.run(Kite.instance!!, task, retired) ?: return null
    // Run now tasks are not tracked in ScriptContext as they execute immediately
    return task
}

/*
*
* @param delayTicks The time delay to pass before executing the task.
* @param task The task to run.
* @param retired The task to run when the entity is retired.
* * @return The {@link ScheduledTask} that represents the scheduled task.
 */
context(context: ScriptContext)
fun EntityScheduler.runDelayed(
    delayTicks: Long,
    task: (ScheduledTask) -> Unit,
    retired: () -> Unit = {}
): ScheduledTask? {
    val task = this.runDelayed(
        Kite.instance!!,
        {
            task(it)
            // Remove the task from the context's list after execution
            context.foliaTasks.remove(it)
        },
        retired,
        delayTicks,
    )
    if (task == null) {
        return null
    }
    context.foliaTasks.add(task)
    return task
}

/*
* @param initialDelayTicks The time delay to pass before the first execution of the task.
* @param periodTicks The time between task executions after the first execution of the task.
* @param task The task to run.
* @param retired The task to run when the entity is retired. Default is an empty function.
* @return The {@link ScheduledTask} that represents the scheduled task.
 */
context(context: ScriptContext)
fun EntityScheduler.runAtFixedRate(
    initialDelayTicks: Long = 0,
    periodTicks: Long,
    task: (ScheduledTask) -> Unit,
    retired: () -> Unit = {}
): ScheduledTask? {
    //TODO: Figure out how to correctly remove repeating tasks from the context's list upon cancellation
    val task = this.runAtFixedRate(
        Kite.instance!!,
        task,
        retired,
        initialDelayTicks,
        periodTicks,
    )
    if (task == null) {
        return null
    }
    context.foliaTasks.add(task)
    return task
}