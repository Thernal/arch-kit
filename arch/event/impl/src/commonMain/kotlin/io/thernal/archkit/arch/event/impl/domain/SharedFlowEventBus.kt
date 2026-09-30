package io.thernal.archkit.arch.event.impl.domain

import io.thernal.archkit.arch.event.api.domain.BaseEvent
import io.thernal.archkit.arch.event.api.domain.EventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

private const val BUFFER = 64

/**
 * [EventBus] over one shared flow of every event; a subscriber filters by type. One flow rather than
 * one per class means an observer of a supertype sees its subtypes, and nothing is created lazily
 * under concurrent access. A slow subscriber holds up to [BUFFER] events before [fire] suspends.
 */
class SharedFlowEventBus : EventBus {
    private val events = MutableSharedFlow<BaseEvent>(extraBufferCapacity = BUFFER)

    override suspend fun fire(event: BaseEvent) {
        events.emit(event)
    }

    @Suppress("UNCHECKED_CAST") // Filtered by isInstance just before.
    override fun <T : BaseEvent> observe(type: KClass<T>): Flow<T> {
        return events.filter { type.isInstance(it) }.map { it as T }
    }
}
