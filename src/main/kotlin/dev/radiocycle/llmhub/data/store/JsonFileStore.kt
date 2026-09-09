package dev.radiocycle.llmhub.data.store

import dev.radiocycle.llmhub.core.AppJson
import dev.radiocycle.llmhub.core.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Tiny durable store: one JSON file per collection inside app storage, mirrored into a
 * [StateFlow] the UI observes. Writes are serialised through a mutex and go via a temp file so a
 * crash mid-write cannot truncate the previous state.
 */
class JsonFileStore<T>(
    baseDir: File,
    fileName: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
    private val scope: CoroutineScope,
) {
    private val file = File(baseDir.apply { mkdirs() }, fileName)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(readBlocking())
    val state: StateFlow<T> = _state.asStateFlow()

    val value: T get() = _state.value

    private fun readBlocking(): T = try {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else default()
    } catch (t: Throwable) {
        Log.w(TAG, "Corrupt store ${file.name}, falling back to defaults", t)
        default()
    }

    /** Applies [transform] to the current value, publishes it, then persists asynchronously. */
    fun update(transform: (T) -> T) {
        val next = transform(_state.value)
        _state.value = next
        scope.launch { persist(next) }
    }

    suspend fun updateAndAwait(transform: (T) -> T) {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
    }

    private suspend fun persist(value: T) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(AppJson.encodeToString(serializer, value))
                try {
                    Files.move(
                        tmp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: Exception) {
                    if (!tmp.renameTo(file)) {
                        file.writeText(tmp.readText())
                        tmp.delete()
                    }
                }
            }.onFailure { Log.e(TAG, "Failed to persist ${file.name}", it) }
        }
    }

    private companion object {
        const val TAG = "JsonFileStore"
    }
}
