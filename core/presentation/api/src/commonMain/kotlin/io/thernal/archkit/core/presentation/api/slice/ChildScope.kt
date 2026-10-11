package io.thernal.archkit.core.presentation.api.slice

import io.thernal.archkit.core.presentation.api.store.Partial
import io.thernal.archkit.core.presentation.api.store.StoreScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

/**
 * A child's view of its parent's store: one part of the state, effects routed through [onEffect], and a
 * scope of its own. Once closed, it changes nothing and emits nothing: a closed child's late result is
 * dropped, not written over whatever the parent holds there now.
 */
internal class ChildScope<PS, PE, S, E>(
    private val parent: StoreScope<PS, PE>,
    private val get: (PS) -> S?,
    private val set: PS.(S) -> PS,
    private val onEffect: StoreScope<PS, PE>.(E) -> Unit,
) : StoreScope<S, E> {
    private var last: S = checkNotNull(get(parent.state)) { "A child starts with its state in the parent's" }
    private var isOpen = true

    override val state: S
        get() {
            get(parent.state)?.let { last = it }
            return last
        }

    override val states: Flow<S> = parent.states.mapNotNull(get).distinctUntilChanged()
    override val subscribers: StateFlow<Int> get() = parent.subscribers

    override val coroutineScope: CoroutineScope = CoroutineScope(
        parent.coroutineScope.coroutineContext + SupervisorJob(parent.coroutineScope.coroutineContext[Job]),
    )

    override fun reduce(partial: Partial<S>) {
        if (!isOpen) {
            return
        }
        parent.reduce(
            Partial { state ->
                val part = get(state)
                if (part == null) {
                    state
                } else {
                    state.set(partial.reduce(part))
                }
            },
        )
    }

    override fun emit(effect: E) {
        if (isOpen) {
            parent.onEffect(effect)
        }
    }

    override suspend fun <T> busy(block: suspend () -> T): T {
        return parent.busy(block)
    }

    fun close() {
        isOpen = false
        coroutineScope.cancel()
    }
}
