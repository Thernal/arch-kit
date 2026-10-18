package io.thernal.archkit.core.presentation.api.store.observer

import io.thernal.archkit.core.presentation.api.logic.action.Policy
import io.thernal.archkit.core.presentation.api.store.state.Partial

/** Something that went through a store, with the store and the chain of work it belongs to. */
sealed interface StoreEvent {
    val store: StoreName
    val trace: StoreTrace

    data class Intent(
        override val store: StoreName,
        override val trace: StoreTrace,
        val intent: Any?,
    ) : StoreEvent

    data class Action(
        override val store: StoreName,
        override val trace: StoreTrace,
        val action: Any,
        val policy: Policy,
        val outcome: ActionOutcome,
    ) : StoreEvent

    /** Only when the state changed. */
    data class State(
        override val store: StoreName,
        override val trace: StoreTrace,
        val partial: Partial<*>,
        val previous: Any?,
        val state: Any?,
    ) : StoreEvent

    data class Effect(
        override val store: StoreName,
        override val trace: StoreTrace,
        val effect: Any?,
    ) : StoreEvent
}
