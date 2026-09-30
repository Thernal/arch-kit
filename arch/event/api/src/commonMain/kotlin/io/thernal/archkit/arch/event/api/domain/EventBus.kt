package io.thernal.archkit.arch.event.api.domain

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/** A cross-feature, fire-and-forget event: something happened that others may react to. */
interface BaseEvent

/**
 * Events between features that do not know each other — "session expired", "cart changed". Not state:
 * an event fired while nobody observes is gone. A subscriber sees every event of the type it asks
 * for, subtypes included.
 */
interface EventBus {
    suspend fun fire(event: BaseEvent)

    fun <T : BaseEvent> observe(type: KClass<T>): Flow<T>
}

inline fun <reified T : BaseEvent> EventBus.observe(): Flow<T> {
    return observe(T::class)
}
