package com.garickbox.jecwebgo.data.store

import android.content.Context
import com.garickbox.jecwebgo.domain.model.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object SettingsStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "settings.json")

    fun load(ctx: Context): Settings = try {
        val f = file(ctx)
        if (!f.exists()) Settings() else json.decodeFromString<Settings>(f.readText())
    } catch (e: Exception) { Settings() }

    fun save(ctx: Context, s: Settings) {
        file(ctx).writeText(json.encodeToString(s))
    }
}
