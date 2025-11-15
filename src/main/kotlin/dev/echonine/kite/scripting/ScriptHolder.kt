package dev.echonine.kite.scripting

import dev.echonine.kite.util.extensions.nameWithoutExtensions
import java.io.File

data class ScriptHolder(val name: String, val entryPoint: File) {

    companion object {
        fun fromName(name: String, scriptsDirectory: File): ScriptHolder? {
            val files = scriptsDirectory.listFiles() ?: emptyArray()
            // Returning ScriptHolder of specified script file, if exists.
            if (files.contains(File(scriptsDirectory, "$name.kite.kts")))
                return ScriptHolder(name, File(scriptsDirectory, "$name.kite.kts"))
            // Looking for a script directory with specified name.
            val scriptFolder = files.find {
                it.isDirectory && it.nameWithoutExtensions == name && File(it, "main.kite.kts").exists()
            }
            // Returning ScriptHolder of specified script directory, if exists.
            if (scriptFolder != null)
                return ScriptHolder(name, File(scriptFolder, "main.kite.kts"))
            // Otherwise, no such script exist - returning null.
            return null
        }
    }

}