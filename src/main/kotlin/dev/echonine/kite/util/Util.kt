package dev.echonine.kite.util

import dev.echonine.kite.Kite
import java.io.File
import kotlin.io.path.Path

fun getKiteDirectory() = Path(Kite.instance?.dataFolder?.path ?: System.getProperty("user.dir", "."))

fun getScriptsDirectory() = getKiteDirectory().resolve("scripts")

val isServerAvailable by lazy {
    try {
        return@lazy Class.forName("org.bukkit.Server") != null
    } catch (e: ClassNotFoundException) {
        return@lazy false
    }
}

fun isEntryPoint(file: File): Boolean {
    val absoluteFilePath = file.absoluteFile.toPath()
    // plugins/Kite/scripts/test.kite.kts
    if (file.name.endsWith(".kite.kts") && getScriptsDirectory().toAbsolutePath() == absoluteFilePath.parent)
        return true
    // plugins/Kite/scripts/foo/main.kite.kts
    if (file.name == "main.kite.kts" && getScriptsDirectory().toAbsolutePath() == absoluteFilePath.parent?.parent)
        return true
    // ...
    return false
}