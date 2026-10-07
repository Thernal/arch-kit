package io.thernal.archkit.core.presentation.api.plugin.effect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.presentation.api.plugin.PluginContext
import io.thernal.archkit.core.presentation.api.plugin.factory
import io.thernal.archkit.core.presentation.api.plugin.ownerName

/** The ViewModel's effect handler, closed with it. Compiles only in a ViewModel. */
context(context: PluginContext, viewModel: ViewModel)
fun <E : ViewEffect> EffectHandler(log: Boolean = true): EffectHandler<E> {
    return context.plugins.factory<EffectFactory>()
        .create<E>(owner = viewModel.ownerName(), log = log, scope = viewModel.viewModelScope)
        .also { viewModel.addCloseable(it) }
}

context(_: ViewModel, plugin: EffectPlugin<E>)
fun <E : ViewEffect> launchEffect(effect: E) {
    plugin.effectHandler.launchEffect(effect)
}
