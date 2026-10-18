package io.thernal.archkit.core.presentation.api.store.observer

/**
 * One chain of work in a store: everything an intent causes — its actions, the states it reduces and the
 * effects it emits, however long its coroutines run — shares the intent's trace. [id] is unique within the
 * store ([StoreName]). Work a phase starts by itself has a trace of its own, whose [parent] is the trace that
 * entered the phase.
 */
data class StoreTrace(
    val id: Long,
    val origin: TraceOrigin,
    val parent: StoreTrace? = null,
)
