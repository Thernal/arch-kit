package io.thernal.archkit.core.presentation.api.plugin.effect.message

import androidx.compose.runtime.Immutable
import io.thernal.archkit.core.presentation.api.plugin.effect.BaseEffect
import io.thernal.archkit.core.presentation.api.string.UiString

/** A message for the user — a snackbar or a toast, as the app's [MessageEffectHandler] decides. */
@Immutable
data class MessageEffect(
    val message: UiString,
    val type: MessageType = MessageType.ERROR,
) : BaseEffect
