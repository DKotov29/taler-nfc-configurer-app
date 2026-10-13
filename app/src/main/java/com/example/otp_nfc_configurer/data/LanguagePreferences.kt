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
