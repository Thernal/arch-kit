package io.thernal.archkit.core.presentation.api.store.scope

import io.thernal.archkit.core.presentation.api.logic.action.Policy
import io.thernal.archkit.core.presentation.api.store.observer.ActionOutcome
import io.thernal.archkit.core.presentation.api.store.observer.StoreTrace
import io.thernal.archkit.core.presentation.api.store.observer.TraceOrigin

/**
 * A scope that knows the chain of work ([StoreTrace]) it does, so the store's observers can tell which intent
 * caused what. The store hands every logic one, and the building blocks — actions, phases, slices, slots —
 * carry the trace along through it; a logic never needs it. A scope that is not one, a hand-made fake in a
 * test, is simply not traced.
 */
interface TracedScope<S, in E> : StoreScope<S, E> {
    val trace: StoreTrace

    /**
     * The chain the store handles on its thread right now — an intent, the start, an effect a child sent up —
     * or null between them. A child's intent or a slot's opening joins it, since a child's scope is created
     * once and cannot know who calls it.
     */
    val handling: StoreTrace?

    /** The chain whose change made the state what it is now: the phase work it wakes up is that chain's. */
    val stateTrace: StoreTrace?

    fun open(
        origin: TraceOrigin,
        parent: StoreTrace?,
    ): StoreTrace

    /** This scope, doing the work of [trace]. */
    fun traced(trace: StoreTrace): TracedScope<S, E>

    /** Runs [block] as the store's handling of [trace] ([handling]). */
    fun <T> handle(
        trace: StoreTrace,
        block: () -> T,
    ): T

    fun reportIntent(intent: Any?)

    fun reportAction(
        action: Any,
        policy: Policy,
        outcome: ActionOutcome,
    )
}
