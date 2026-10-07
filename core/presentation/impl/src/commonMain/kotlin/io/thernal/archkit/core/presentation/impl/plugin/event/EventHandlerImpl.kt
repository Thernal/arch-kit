package io.thernal.archkit.core.presentation.impl.plugin.event

import io.thernal.archkit.core.event.api.domain.BaseEvent
import io.thernal.archkit.core.event.api.domain.EventBus
import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.event.EventHandler
import io.thernal.archkit.core.presentation.impl.plugin.PRESENTATION_LOG_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

internal class EventHandlerImpl(
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val owner: String,
    private val logger: PresentationLogger?,
) : EventHandler {
    override fun fire(event: BaseEvent) {
        logger?.log(tag = PRESENTATION_LOG_TAG, message = "$owner fired · $event", error = null)
        scope.launch { eventBus.fire(event) }
    }

    override fun <T : BaseEvent> observe(
        type: KClass<T>,
        onEvent: suspend (T) -> Unit,
    ): Job {
        return eventBus.observe(type)
            .onEach { event ->
                logger?.log(tag = PRESENTATION_LOG_TAG, message = "$owner caught · $event", error = null)
                onEvent(event)
            }
            .launchIn(scope)
    }
}
