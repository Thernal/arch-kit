package io.thernal.archkit.arch.presentation.api.presentation.string

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.thernal.archkit.arch.domain.failure.Failure
import io.thernal.archkit.arch.domain.failure.FieldError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Every user-facing text in state, effects and forms, resolved only where it is drawn — so a
 * ViewModel never touches resources and a test compares values, not strings.
 */
@Immutable
sealed interface UiString {
    data class Dynamic(
        val value: String,
    ) : UiString

    /** A Compose Resources string, with format arguments. */
    data class Resource(
        val resource: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiString

    /**
     * A key the app's [StringLookup] resolves (remote translations, a key-based catalog), and what to
     * show when it does not. The kit's own texts are these, with English fallbacks.
     */
    data class Localized(
        val key: String,
        val fallback: UiString = Dynamic(key),
    ) : UiString

    data class Composite(
        val parts: List<UiString>,
        val separator: String = "",
    ) : UiString
}

/** Resolves [UiString.Localized] keys: the app's translations. Null for a key it does not know. */
fun interface StringLookup {
    fun find(key: String): String?
}

/** Installed at the root by an app with key-based translations; by default every key falls back. */
val LocalStringLookup = staticCompositionLocalOf { StringLookup { null } }

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
 * Resolves outside composition — a snackbar host shown from `MessageEffectHandler`, a notification.
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

fun String.ui(): UiString {
    return UiString.Dynamic(this)
}

fun StringResource.ui(vararg args: Any): UiString {
    return UiString.Resource(resource = this, args = args.toList())
}

/** `failure.<status error>` for the app to translate; the failure's message otherwise. */
fun Failure.toUiString(): UiString {
    val fallback = UiString.Localized(
        key = "failure.unknown",
        fallback = UiString.Dynamic(message ?: "Something went wrong"),
    )
    val error = status.error ?: return fallback
    return UiString.Localized(key = "failure.${error.lowercase()}", fallback = fallback)
}

/** `field_error.<error>` for the app to translate; the raw error otherwise. */
fun FieldError.toUiString(): UiString {
    return UiString.Localized(key = "field_error.${error.lowercase()}", fallback = UiString.Dynamic(error))
}
