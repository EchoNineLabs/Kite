package dev.echonine.kite.scripting.cache

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.echonine.kite.Kite
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ImportsCache {
    private val mutex = Mutex()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val directory = Kite.Structure.CACHE_DIR.resolve("imports")

    private val typeToken = object : TypeToken<MutableSet<String>>() {}

    suspend fun invalidate(name: String) = mutex.withLock {
        directory.resolve("$name.json").delete()
    }

    suspend fun append(name: String, dependencies: Collection<String>) {
        // Saving contents to the file.
        val contents = this.read(name)
        // Updating contents.
        contents.addAll(dependencies)
        // Creating parent directories and file in case it does not exist.
        val file = directory.resolve("$name.json").also {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        // Writing to the file...By using Mutex here, we ensure writes are synchronized.
        mutex.withLock {
            file.bufferedWriter().use {
                try {
                    gson.toJson(contents, typeToken.type, it)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun read(name: String): MutableSet<String> = mutex.withLock {
        // Creating parent directories and file in case it does not exist.
        val file = directory.resolve("$name.json").also {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        return file.bufferedReader().use {
            try {
                gson.fromJson(it, typeToken.type) as? MutableSet<String>
            } catch (e: Throwable) {
                e.printStackTrace()
                return@use null
            }
        } ?: mutableSetOf()
    }

}