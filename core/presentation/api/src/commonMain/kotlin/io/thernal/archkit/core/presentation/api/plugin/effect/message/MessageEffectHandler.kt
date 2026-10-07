package io.thernal.archkit.core.presentation.api.plugin.effect.message

import androidx.compose.runtime.staticCompositionLocalOf

/** Shows a [MessageEffect] — the app's snackbar or toast. Installed at the root. */
fun interface MessageEffectHandler {
    suspend fun show(effect: MessageEffect)
}

/** By default messages are dropped — an isolated preview needs no snackbar host. */
val LocalMessageEffectHandler = staticCompositionLocalOf { MessageEffectHandler { } }
