package io.thernal.archkit.core.presentation.api.store.observer

import io.thernal.archkit.core.presentation.api.store.state.Partial

/**
 * Sees everything that goes through a store, in order: for logs in debug builds, for analytics, and for
 * the test DSL. Named [Partial]s are why logs read well: `SearchPartial.Results`, not `<lambda>`.
 *
 * The store calls [onEvent], which knows the store and the trace of each event and also sees actions; its
 * default hands intents, states and effects to the three callbacks below, so an observer overrides
 * whichever it needs.
 */
interface StoreObserver {
    fun onEvent(event: StoreEvent) {
        when (event) {
            is StoreEvent.Intent -> onIntent(event.intent)

            is StoreEvent.State -> onState(
                partial = event.partial,
                previous = event.previous,
                state = event.state,
            )

            is StoreEvent.Effect -> onEffect(event.effect)

            is StoreEvent.Action -> Unit
        }
    }

    fun onIntent(intent: Any?) {}

    /** Called only when the state changed. */
    fun onState(
        partial: Partial<*>,
        previous: Any?,
        state: Any?,
    ) {}

    fun onEffect(effect: Any?) {}
}
