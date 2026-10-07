package io.thernal.archkit.core.presentation.api.plugin.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.event.api.domain.BaseEvent
import io.thernal.archkit.core.presentation.api.plugin.PluginContext
import io.thernal.archkit.core.presentation.api.plugin.factory
import io.thernal.archkit.core.presentation.api.plugin.ownerName
import kotlinx.coroutines.Job

/** The ViewModel's event handler. Compiles only in a ViewModel. */
context(context: PluginContext, viewModel: ViewModel)
fun EventHandler(): EventHandler {
    return context.plugins.factory<EventFactory>().create(
        owner = viewModel.ownerName(),
        scope = viewModel.viewModelScope,
    )
}

fun EventPlugin.fireEvent(event: BaseEvent) {
    eventHandler.fire(event)
}

/** Reacts to every [T] fired while the ViewModel lives. */
inline fun <reified T : BaseEvent> EventPlugin.observeEvent(noinline onEvent: suspend (T) -> Unit): Job {
    return eventHandler.observe(type = T::class, onEvent = onEvent)
}
