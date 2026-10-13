package com.example.otp_nfc_configurer.model

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text shown to the user, resolved against the current locale only when it is displayed,
 * so state kept in the ViewModel follows a language change. Arguments may be [UiText] themselves.
 */
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *resolveArgs(args, context))
        is Plural -> context.resources.getQuantityString(id, count, *resolveArgs(args, context))
    }

    private fun resolveArgs(args: List<Any>, context: Context): Array<Any> =
        args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** Plural text whose count is also the first format argument. */
fun uiPlural(@PluralsRes id: Int, count: Int): UiText = UiText.Plural(id, count, listOf(count))
