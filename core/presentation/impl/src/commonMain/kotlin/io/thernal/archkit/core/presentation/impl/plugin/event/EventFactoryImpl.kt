package io.thernal.archkit.core.presentation.impl.plugin.event

import io.thernal.archkit.core.event.api.domain.EventBus
import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.event.EventFactory
import io.thernal.archkit.core.presentation.api.plugin.event.EventHandler
import kotlinx.coroutines.CoroutineScope

class EventFactoryImpl(
    private val eventBus: EventBus,
    private val logger: PresentationLogger? = null,
) : EventFactory {
    override fun create(
        owner: String,
        scope: CoroutineScope,
    ): EventHandler {
        return EventHandlerImpl(eventBus = eventBus, scope = scope, owner = owner, logger = logger)
    }
}
