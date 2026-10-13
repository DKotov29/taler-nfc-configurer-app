/*
 * SPDX-FileCopyrightText: 2026 Taler Systems S.A.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Taler.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.otp_nfc_configurer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class SavedConfigKind { SLOT, GLOBAL }

/** A named set of field values stored locally ("Save Template" / "Load Template"). Never contains secrets. */
data class SavedConfig(
    val name: String,
    val kind: SavedConfigKind,
    val values: Map<String, String>
)

class SavedConfigRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _configs = MutableStateFlow(load())
    val configs: StateFlow<List<SavedConfig>> = _configs.asStateFlow()

    /** Saves [config], replacing an existing one with the same name and kind. */
    fun save(config: SavedConfig) {
        update(_configs.value.filterNot { it.name == config.name && it.kind == config.kind } + config)
    }

    fun delete(config: SavedConfig) {
        update(_configs.value - config)
    }

    private fun update(configs: List<SavedConfig>) {
        val sorted = configs.sortedBy { it.name.lowercase() }
        _configs.value = sorted
        prefs.edit().putString(KEY_CONFIGS, encode(sorted)).apply()
    }

    private fun load(): List<SavedConfig> = try {
        decode(prefs.getString(KEY_CONFIGS, null) ?: "[]")
    } catch (_: Exception) {
        emptyList()
    }

    private fun encode(configs: List<SavedConfig>): String = JSONArray().apply {
        configs.forEach { config ->
            put(JSONObject().apply {
                put("name", config.name)
                put("kind", config.kind.name)
                put("values", JSONObject(config.values))
            })
        }
    }.toString()

    private fun decode(json: String): List<SavedConfig> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val values = obj.getJSONObject("values")
            SavedConfig(
                name = obj.getString("name"),
                kind = SavedConfigKind.valueOf(obj.getString("kind")),
                values = values.keys().asSequence().associateWith { values.getString(it) }
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "saved_configs"
        const val KEY_CONFIGS = "configs"
    }
}
