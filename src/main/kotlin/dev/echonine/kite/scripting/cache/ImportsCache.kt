package dev.echonine.kite.scripting.cache

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.echonine.kite.Kite
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias DependencyTree = MutableMap<String, MutableSet<String>>

class ImportsCache {
    private val mutex = Mutex()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = Kite.Structure.CACHE_DIR.resolve(".imports")

    private val typeToken = object : TypeToken<DependencyTree>() { /* TYPE MARKER */ }

    var cache: DependencyTree = mutableMapOf()
        private set

    init {
        // Creating parent directories and file in case it does not exist.
        file.parentFile.mkdirs()
        file.createNewFile()
        // Reading file contents.
        cache = file.bufferedReader().use { gson.fromJson(it, typeToken) } ?: mutableMapOf()
    }

    suspend fun invalidate(name: String) = mutex.withLock {
        cache.remove(name)
        // Saving contents to the file.
        this.save()
    }

    suspend fun append(name: String, dependencies: Collection<String>) = mutex.withLock {
        // Putting list of dependencies / imports in the map.
        cache.computeIfAbsent(name) { mutableSetOf() }.addAll(dependencies)
        // Saving contents to the file.
        this.save()
    }

    private fun save() {
        // Creating parent directories and file in case it does not exist.
        file.parentFile.mkdirs()
        file.createNewFile()
        // Saving contents to the file.
        file.bufferedWriter().use {
            try {
                gson.toJson(cache, typeToken.type, it)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

}