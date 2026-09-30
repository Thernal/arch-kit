package io.thernal.archkit.arch.presentation.api.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.arch.event.api.domain.BaseEvent
import io.thernal.archkit.arch.presentation.api.presentation.plugin.PluginContext
import io.thernal.archkit.arch.presentation.api.presentation.plugin.PluginFactory
import io.thernal.archkit.arch.presentation.api.presentation.plugin.factory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.reflect.KClass

/** Fires and observes `EventBus` events on the ViewModel's scope. */
interface EventHandler {
    fun fire(event: BaseEvent)

    fun <T : BaseEvent> observe(
        type: KClass<T>,
        onEvent: suspend (T) -> Unit,
    ): Job
}

interface EventFactory : PluginFactory {
    fun create(
        owner: String,
        scope: CoroutineScope,
    ): EventHandler
}

interface EventPlugin : PluginContext {
    val eventHandler: EventHandler
}

/** The ViewModel's event handler. Compiles only in a ViewModel. */
context(context: PluginContext, viewModel: ViewModel)
fun EventHandler(): EventHandler {
    return context.plugins.factory<EventFactory>().create(
        owner = viewModel::class.simpleName.orEmpty(),
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
