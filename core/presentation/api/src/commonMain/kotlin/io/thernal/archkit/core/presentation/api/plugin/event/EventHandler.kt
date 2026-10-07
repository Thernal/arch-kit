package io.thernal.archkit.core.presentation.api.plugin.event

import io.thernal.archkit.core.event.api.domain.BaseEvent
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
