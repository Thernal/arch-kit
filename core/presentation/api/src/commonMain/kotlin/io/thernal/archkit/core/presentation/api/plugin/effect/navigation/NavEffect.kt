package io.thernal.archkit.core.presentation.api.plugin.effect.navigation

import androidx.compose.runtime.Immutable
import io.thernal.archkit.core.presentation.api.plugin.effect.BaseEffect

/**
 * A navigation command a ViewModel cannot run itself — the navigator lives in the screen's
 * composition. [N] is the app's navigator type (nav-kit's `Navigator`, or any other); the app's
 * [NavEffectHandler] runs [command] on it.
 */
@Immutable
data class NavEffect<N>(
    val command: N.() -> Unit,
) : BaseEffect
