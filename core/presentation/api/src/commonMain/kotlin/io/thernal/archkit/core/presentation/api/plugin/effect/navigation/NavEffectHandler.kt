package io.thernal.archkit.core.presentation.api.plugin.effect.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** Runs a [NavEffect] on the app's navigator. Installed at the root, or wherever the navigator is. */
fun interface NavEffectHandler {
    fun run(effect: NavEffect<*>)
}

/** By default navigation is dropped — an isolated preview has no navigator. */
val LocalNavEffectHandler = staticCompositionLocalOf { NavEffectHandler { } }
