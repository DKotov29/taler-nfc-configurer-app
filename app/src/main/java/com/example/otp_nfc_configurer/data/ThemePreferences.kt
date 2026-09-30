package com.example.otp_nfc_configurer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme(val title: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    private fun loadTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name)
        return runCatching { AppTheme.valueOf(name!!) }.getOrDefault(AppTheme.SYSTEM)
    }

    private companion object {
        const val KEY_THEME = "app_theme"
    }
}