package com.peaspilaf.copyparty.data

import com.peaspilaf.copyparty.core.AppState
import kotlinx.serialization.json.Json
import java.io.File

/** Loads and saves the app state (users, shares, settings). Implemented over a JSON file on disk. */
interface ConfigStore {
    fun load(): AppState
    fun save(state: AppState)
}

class JsonConfigStore(private val file: File) : ConfigStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override fun load(): AppState = runCatching {
        if (file.isFile) json.decodeFromString(AppState.serializer(), file.readText()) else AppState()
    }.getOrDefault(AppState())

    override fun save(state: AppState) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), state))
        if (file.exists() && !file.delete()) {
            // Renaming over the old file is atomic enough on the same filesystem; a stuck old file
            // must not silently keep stale config around.
            file.writeText(tmp.readText())
            tmp.delete()
            return
        }
        tmp.renameTo(file)
    }
}
