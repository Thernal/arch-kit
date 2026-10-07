package io.thernal.archkit.core.presentation.api.plugin.effect.navigation

import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectPlugin

/**
 * Navigates from a ViewModel: the command runs on the screen's navigator, of type [N]. An app
 * usually defines a one-line shorthand for its own navigator type:
 * `fun navigation(command: Navigator.() -> Unit) = navigation<Navigator>(command)`.
 */
context(_: ViewModel, plugin: EffectPlugin<*>)
fun <N> navigation(command: N.() -> Unit) {
    plugin.effectHandler.launchBaseEffect(NavEffect(command))
}
