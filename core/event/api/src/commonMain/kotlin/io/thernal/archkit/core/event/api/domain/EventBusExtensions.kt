package io.thernal.archkit.core.event.api.domain

import kotlinx.coroutines.flow.Flow

inline fun <reified T : BaseEvent> EventBus.observe(): Flow<T> {
    return observe(T::class)
}
