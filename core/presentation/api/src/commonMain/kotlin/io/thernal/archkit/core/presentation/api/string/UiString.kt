package io.thernal.archkit.core.presentation.api.string

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource

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
