package io.thernal.archkit.core.presentation.impl.store.observer

import io.thernal.archkit.core.presentation.api.store.state.Partial

/**
 * Sees everything that goes through a store, in order: for logs in debug builds, for analytics, and for
 * the test DSL. Named [Partial]s are why logs read well: `SearchPartial.Results`, not `<lambda>`.
 */
interface StoreObserver {
    fun onIntent(intent: Any?) {}

    /** Called only when the state changed. */
    fun onState(
        partial: Partial<*>,
        previous: Any?,
        state: Any?,
    ) {}

    fun onEffect(effect: Any?) {}
}
