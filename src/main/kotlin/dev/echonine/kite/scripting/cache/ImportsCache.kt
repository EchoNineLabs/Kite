package dev.echonine.kite.scripting.cache

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.echonine.kite.Kite
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.path.Path

typealias DependencyTree = MutableMap<String, List<String>>

class ImportsCache {
    private val mutex = Mutex()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = Path(Kite.instance?.dataFolder?.path ?: System.getProperty("user.dir", "."), "cache", ".imports").toFile()

    private val typeToken = object : TypeToken<DependencyTree>() { /* TYPE MARKER */ }

    var cache: DependencyTree = mutableMapOf()
        private set

    suspend fun write(name: String, dependencies: List<String>) = mutex.withLock {
        // Creating parent directories and file in case it does not exist.
        file.parentFile.mkdirs()
        file.createNewFile()
        // Reading file contents.
        cache = file.bufferedReader().use { gson.fromJson(it, typeToken) } ?: mutableMapOf()
        // Putting list of dependencies / imports to the map.
        cache[name] = dependencies
        // Saving contents to the file.
        file.bufferedWriter().use { gson.toJson(cache, typeToken.type, it) }
    }

}