package dev.echonine.kite.scripting.libraries

import com.alessiodp.libby.LibraryManager
import com.alessiodp.libby.logging.LogLevel
import com.alessiodp.libby.logging.adapters.JDKLogAdapter
import dev.echonine.kite.Kite
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

// This log adapter only logs warnings and errors.
// Logging each (transitive) dependency is too verbose.
private class SoftLogAdapter(logger: Logger) : JDKLogAdapter(logger) {

    override fun log(level: LogLevel, message: String?) {
        if (level >= LogLevel.WARN)
            super.log(level, message)
    }

    override fun log(level: LogLevel, message: String?, throwable: Throwable?) {
        if (level >= LogLevel.WARN)
            super.log(level, message, throwable)
    }

}

// This class is based on BukkitLibraryLoader and exists solely to track resolved dependencies.
// Neither LibraryLoader#downloadLibrary nor LibraryLoader#loadLibrary do not return a comprehensive list of resolved dependencies.
class KiteLibraryManager : LibraryManager(SoftLogAdapter(Kite.INSTANCE!!.logger), Kite.Structure.KITE_DIR.toPath(), Kite.Structure.LIBS_DIR.name) {
    val resolvedPaths = ConcurrentHashMap.newKeySet<File>()!!

    // See that super method is not called here.
    // We do not need (and do not want to) add any of script libraries to the Kite plugin classpath.
    override fun addToClasspath(file: Path) {
        resolvedPaths.add(file.toFile())
    }

}