package io.thernal.archkit.core.presentation.api.logic

import io.thernal.archkit.core.presentation.api.slice.Slice
import io.thernal.archkit.core.presentation.api.store.StoreScope

/**
 * A unit of presentation logic — the one thing every level implements. It does not know who hosts it: a
 * screen's ViewModel, a component's ViewModel and a parent's [Slice] run it the same way, so a piece of
 * logic moves between them without a change.
 *
 * An instance belongs to one host, because it may keep that host's bookkeeping (running actions, a
 * machine). Create a new one for every host.
 */
interface Logic<S, I, E> {
    /** Runs once, when the logic is hosted: binders and observers start here. */
    fun StoreScope<S, E>.onStart() {}

    fun StoreScope<S, E>.onIntent(intent: I)
}
