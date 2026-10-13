package com.example.otp_nfc_configurer.data

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.otp_nfc_configurer.R
import java.util.Locale

/** Selectable UI languages; [nativeName] is shown untranslated so users always find their language. */
enum class AppLanguage(val tag: String?, val nativeName: String?, @StringRes val label: Int) {
    SYSTEM(null, null, R.string.language_system),
    ENGLISH("en", "English", R.string.language_english),
    GERMAN("de", "Deutsch", R.string.language_german),
    SWISS_GERMAN("gsw-CH", "Bärndütsch", R.string.language_swiss_german),
    UKRAINIAN("uk", "Українська", R.string.language_ukrainian)
}

/**
 * Per-app language via AppCompat: stored by the system on Android 13+ (also editable in the system
 * app settings) and by AppCompat's AppLocalesMetadataHolderService below. Changing it recreates the activity.
 */
object LanguagePreferences {

    fun current(): AppLanguage {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.tag != null && Locale.forLanguageTag(it.tag).language == locale.language }
            ?: AppLanguage.SYSTEM
    }

    fun set(language: AppLanguage) {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
