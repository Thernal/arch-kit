package io.thernal.archkit.core.presentation.api.presentation.model

import androidx.compose.runtime.Immutable
import io.thernal.archkit.core.presentation.api.presentation.string.UiString

enum class MessageType { SUCCESS, INFO, ERROR }

/** A message for the user — a snackbar or a toast, as the app's `MessageEffectHandler` decides. */
@Immutable
data class MessageEffect(
    val message: UiString,
    val type: MessageType = MessageType.ERROR,
) : BaseEffect

/**
 * A navigation command a ViewModel cannot run itself — the navigator lives in the screen's
 * composition. [N] is the app's navigator type (nav-kit's `Navigator`, or any other); the app's
 * `NavEffectHandler` runs [command] on it.
 */
@Immutable
data class NavEffect<N>(
    val command: N.() -> Unit,
) : BaseEffect
