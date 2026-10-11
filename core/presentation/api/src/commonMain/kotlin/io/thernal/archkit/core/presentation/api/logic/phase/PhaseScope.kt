package io.thernal.archkit.core.presentation.api.logic.phase

import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.state.Partial
import kotlin.reflect.KClass

/**
 * What work bound to a phase sees: the phase, a way to change it that does nothing once the phase is
 * left, and the store's effects. A late result can never overwrite the phase the user has moved on to.
 */
class PhaseScope<S : Any, T : S, E>(
    private val store: StoreScope<S, E>,
    private val phase: KClass<T>,
    entered: T,
) {
    private var last: T = entered

    /** The phase now; once it is left, the last value it had. */
    val state: T
        get() {
            val current = store.state
            @Suppress("UNCHECKED_CAST")
            if (phase.isInstance(current)) {
                last = current as T
            }
            return last
        }

    /**
     * Moves from the phase to the state [block] returns — the same phase updated, or another one — and
     * answers that state. Ignored when the store is no longer in the phase, and then answers null.
     */
    fun setState(block: T.() -> S): S? {
        var moved: S? = null
        store.reduce(
            Partial { current ->
                @Suppress("UNCHECKED_CAST")
                if (phase.isInstance(
                        current,
                    )
                ) {
                    (current as T).block().also { moved = it }
                } else {
                    current.also { moved = null }
                }
            },
        )
        return moved
    }

    fun emit(effect: E) {
        store.emit(effect)
    }
}
