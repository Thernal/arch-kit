package io.thernal.archkit.core.presentation.api.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a screen sees of its presentation logic, at every level: the state to draw, where intents go, and
 * effects, each delivered once. A screen written against this does not change when its logic moves from
 * one level to another.
 */
interface Store<S, I, E> {
    val state: StateFlow<S>

    /**
     * Delivered once, to one collector. An effect emitted while no one collects — the screen is in the
     * background — waits for the next collector instead of being lost. A second collector at the same
     * time is a bug (two screens would race for each effect), and fails at once.
     */
    val effects: Flow<E>

    /**
     * True while the logic has work running: launched work, an action, the work of a phase. Not for a
     * binder (`observe`, `whileSubscribed`), which never ends. What holds a component's ViewModel until its
     * work is done.
     */
    val isBusy: StateFlow<Boolean>

    fun postIntent(intent: I)
}
