package dev.echonine.kite.scripting.configuration

import dev.echonine.kite.Kite
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
object KiteScriptingHostConfiguration : ScriptingHostConfiguration({
    jvm {
        baseClassLoader(Kite::class.java.classLoader)
    }
})