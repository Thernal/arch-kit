package io.thernal.archkit.core.presentation.api.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What presentation logic sees of the store that hosts it: the state, the one way to change it, the way
 * out for effects, and a scope that lives as long as the store. A slice sees its own part of the state
 * only, and does not know it. It takes effects of its own type or any narrower one, so a slice with no
 * effects (`Nothing`) or with a subtype of the parent's effects attaches as it is.
 */
interface StoreScope<S, in E> {
    /** The state now. After a suspension, read it again: it may have changed meanwhile. */
    val state: S

    /** The state now and every state after it. */
    val states: Flow<S>

    /**
     * How many collect the store's state now. A screen collects it only while it is visible
     * (`collectAsState` is lifecycle-aware), so this is what [whileSubscribed] runs on.
     */
    val subscribers: StateFlow<Int>

    /**
     * Lives as long as the store — or, for a slice, as long as it is attached. A failure inside it is
     * reported, and cancels neither the store nor its other work.
     */
    val coroutineScope: CoroutineScope

    fun reduce(partial: Partial<S>)

    fun emit(effect: E)

    /**
     * Runs [block] as work of the store: while it runs, the store is busy ([Store.isBusy]). [launch] and the
     * work of a phase are work. A binder ([observe], [whileSubscribed]) is not: it never ends, and a store
     * that is busy forever is a store that cannot be let go.
     */
    suspend fun <T> busy(block: suspend () -> T): T
}
