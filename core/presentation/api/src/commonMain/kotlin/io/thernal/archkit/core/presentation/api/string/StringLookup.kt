package io.thernal.archkit.core.presentation.api.string

import androidx.compose.runtime.staticCompositionLocalOf

/** Resolves [UiString.Localized] keys: the app's translations. Null for a key it does not know. */
fun interface StringLookup {
    fun find(key: String): String?
}

/** Installed at the root by an app with key-based translations; by default every key falls back. */
val LocalStringLookup = staticCompositionLocalOf { StringLookup { null } }
