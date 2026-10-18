package io.thernal.archkit.core.presentation.api.logic.child

import io.thernal.archkit.core.presentation.api.logic.action.Policy
import io.thernal.archkit.core.presentation.api.store.observer.ActionOutcome
import io.thernal.archkit.core.presentation.api.store.observer.StoreTrace
import io.thernal.archkit.core.presentation.api.store.observer.TraceOrigin
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.TracedScope
import io.thernal.archkit.core.presentation.api.store.state.Partial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

/**
 * A child's view of its parent's store: one part of the state, effects routed through [onEffect], and a
 * scope of its own. Once closed, it changes nothing and emits nothing: a closed child's late result is
 * dropped, not written over whatever the parent holds there now.
 *
 * It is created once, with the parent's scope at that moment, so the chain of work an intent of the child
 * belongs to comes from the store: [receive] and [starting] hand the child's logic a view of this scope for
 * the chain the store handles now.
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
        reduceInto(target = parent, partial = partial)
    }

    override fun emit(effect: E) {
        emitInto(target = parent, effect = effect)
    }

    override suspend fun <T> busy(block: suspend () -> T): T {
        return parent.busy(block)
    }

    /** The scope the child's `onStart` runs in: the chain that attaches or opens it. */
    fun starting(): StoreScope<S, E> {
        val tracing = parent as? TracedScope<PS, PE> ?: return this
        return Traced(trace = tracing.handling ?: tracing.trace, tracing = tracing)
    }

    /**
     * The scope the child handles [intent] in, and the intent reported to the store's observers: the chain the
     * store handles now, or a chain of the intent's own when it handles none (an intent posted from launched
     * work).
     */
    fun receive(intent: Any?): StoreScope<S, E> {
        val tracing = parent as? TracedScope<PS, PE> ?: return this
        val trace = tracing.handling ?: tracing.open(origin = TraceOrigin.Intent(intent), parent = null)
        return Traced(trace = trace, tracing = tracing).also { scope -> scope.reportIntent(intent) }
    }

    fun close() {
        isOpen = false
        coroutineScope.cancel()
    }

    private fun reduceInto(
        target: StoreScope<PS, PE>,
        partial: Partial<S>,
    ) {
        if (isOpen) {
            target.reduce(ChildPartial(partial = partial, get = get, set = set))
        }
    }

    private fun emitInto(
        target: StoreScope<PS, PE>,
        effect: E,
    ) {
        if (isOpen) {
            target.onEffect(effect)
        }
    }

    private inner class Traced(
        override val trace: StoreTrace,
        private val tracing: TracedScope<PS, PE>,
    ) : TracedScope<S, E> {
        private val target = tracing.traced(trace)

        override val state: S get() = this@ChildScope.state
        override val states: Flow<S> get() = this@ChildScope.states
        override val subscribers: StateFlow<Int> get() = this@ChildScope.subscribers
        override val coroutineScope: CoroutineScope get() = this@ChildScope.coroutineScope
        override val handling: StoreTrace? get() = tracing.handling
        override val stateTrace: StoreTrace? get() = tracing.stateTrace

        override fun reduce(partial: Partial<S>) {
            reduceInto(target = target, partial = partial)
        }

        // The parent's handler runs as this chain's handling, so a sibling it sends an intent to joins the chain.
        override fun emit(effect: E) {
            tracing.handle(trace) { emitInto(target = target, effect = effect) }
        }

        override suspend fun <T> busy(block: suspend () -> T): T {
            return parent.busy(block)
        }

        override fun open(
            origin: TraceOrigin,
            parent: StoreTrace?,
        ): StoreTrace {
            return tracing.open(origin = origin, parent = parent)
        }

        override fun traced(trace: StoreTrace): TracedScope<S, E> {
            return Traced(trace = trace, tracing = tracing)
        }

        override fun <T> handle(
            trace: StoreTrace,
            block: () -> T,
        ): T {
            return tracing.handle(trace = trace, block = block)
        }

        override fun reportIntent(intent: Any?) {
            target.reportIntent(intent)
        }

        override fun reportAction(
            action: Any,
            policy: Policy,
            outcome: ActionOutcome,
        ) {
            target.reportAction(action = action, policy = policy, outcome = outcome)
        }
    }
}
