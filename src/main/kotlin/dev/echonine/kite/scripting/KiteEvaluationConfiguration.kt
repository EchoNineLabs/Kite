package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.scriptsInstancesSharing

object KiteEvaluationConfiguration : ScriptEvaluationConfiguration({
    scriptsInstancesSharing(true)
    providedProperties(mapOf(
        "plugin" to Kite.instance!!,
        "server" to Kite.instance!!.server
    ))
})