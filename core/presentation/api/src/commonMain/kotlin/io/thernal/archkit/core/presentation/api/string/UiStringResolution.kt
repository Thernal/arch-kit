package io.thernal.archkit.core.presentation.api.string

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun UiString.resolve(): String {
    return when (this) {
        is UiString.Dynamic -> value

        is UiString.Resource -> stringResource(resource = resource, formatArgs = args.toTypedArray())

        is UiString.Localized -> LocalStringLookup.current.find(key) ?: fallback.resolve()

        is UiString.Composite -> {
            val resolved = parts.map { it.resolve() }
            resolved.joinToString(separator)
        }
    }
}

/**
 * Resolves outside composition — a snackbar host the app shows, a notification.
 * [lookup] is the app's `StringLookup` for `Localized` keys (read `LocalStringLookup.current` where it is
 * installed).
 */
suspend fun UiString.load(lookup: StringLookup = StringLookup { null }): String {
    return when (this) {
        is UiString.Dynamic -> value
        is UiString.Resource -> getString(resource = resource, formatArgs = args.toTypedArray())
        is UiString.Localized -> lookup.find(key) ?: fallback.load(lookup)
        is UiString.Composite -> parts.map { it.load(lookup) }.joinToString(separator)
    }
}
