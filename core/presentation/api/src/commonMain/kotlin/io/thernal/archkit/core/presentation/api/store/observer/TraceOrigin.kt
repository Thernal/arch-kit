package io.thernal.archkit.core.presentation.api.store.observer

sealed interface TraceOrigin {
    /** The logic's `onStart`, and the binders it starts there. */
    data object Start : TraceOrigin

    data class Intent(
        val intent: Any?,
    ) : TraceOrigin

    /** A `whileIn` block, started by the state entering [phase]. */
    data class Phase(
        val phase: String,
    ) : TraceOrigin
}
