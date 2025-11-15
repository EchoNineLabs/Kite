package dev.echonine.kite.scripting.configuration

import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.scriptsInstancesSharing

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object KiteEvaluationConfiguration : ScriptEvaluationConfiguration({
    // Enabling instances sharing makes sure no two instances of the same script can exist at the same time.
    scriptsInstancesSharing(true)
})