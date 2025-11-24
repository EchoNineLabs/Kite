package dev.echonine.kite.api

import dev.echonine.kite.scripting.ScriptContext
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation") // ScheduledTask#isCancelled; Does not appear to be overridden by any ScheduledTask impl.
class KiteScheduledTask(private val context: ScriptContext, private val scheduledTask: ScheduledTask) : ScheduledTask by scheduledTask {

    override fun cancel(): ScheduledTask.CancelledState {
        context.foliaTasks.remove(this)
        // Delegating to the cancel() impl.
        return scheduledTask.cancel();
    }

}