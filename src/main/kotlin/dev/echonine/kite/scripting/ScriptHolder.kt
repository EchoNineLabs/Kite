package dev.echonine.kite.scripting

import dev.echonine.kite.Kite
import dev.echonine.kite.util.extensions.nameWithoutExtensions
import java.io.File

data class ScriptHolder(val name: String, val entryPoint: File) {

    companion object {

        /**
         * Returns [ScriptHolder] of the specified script file or directory or `null` if it does not exist.
         */
        fun fromName(name: String, scriptsDirectory: File): ScriptHolder? {
            val files = scriptsDirectory.listFiles() ?: emptyArray()
            // Returning ScriptHolder of the specified script file, if exists.
            if (files.contains(File(scriptsDirectory, "$name.kite.kts")))
                return ScriptHolder(name, File(scriptsDirectory, "$name.kite.kts"))
            // Looking for a script directory with specified name.
            val scriptFolder = files.find {
                it.isDirectory && it.nameWithoutExtensions == name && File(it, "main.kite.kts").exists()
            }
            // Returning ScriptHolder of specified script directory, if exists.
            if (scriptFolder != null)
                return ScriptHolder(name, File(scriptFolder, "main.kite.kts"))
            // Otherwise, no such script exists - returning null.
            return null
        }

        /**
         * Returns `true` if the specified file is an entry point script, `false` otherwise.
         */
        fun isEntryPoint(file: File): Boolean {
            // (Scenario 1) plugins/Kite/scripts/test.kite.kts
            if (file.name.endsWith(".kite.kts") && Kite.Structure.SCRIPTS_DIR.canonicalFile == file.parentFile.canonicalFile)
                return true
            // (Scenario 2) plugins/Kite/scripts/foo/main.kite.kts
            if (file.name == "main.kite.kts" && Kite.Structure.SCRIPTS_DIR.canonicalFile == file.parentFile?.parentFile?.canonicalFile)
                return true
            // Other scenarios are not currently supported. File in question is an entry point script.
            return false
        }

    }

}