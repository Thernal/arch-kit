package io.thernal.archkit.core.event.api.domain

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Events between features that do not know each other — "session expired", "cart changed". Not state:
 * an event fired while nobody observes is gone. A subscriber sees every event of the type it asks
 * for, subtypes included.
 */
interface EventBus {
    suspend fun fire(event: BaseEvent)

    fun <T : BaseEvent> observe(type: KClass<T>): Flow<T>
}
