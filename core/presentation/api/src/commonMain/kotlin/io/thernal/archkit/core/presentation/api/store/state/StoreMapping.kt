package io.thernal.archkit.core.presentation.api.store.state

import io.thernal.archkit.core.presentation.api.store.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The store with its state mapped for the screen: the logic keeps the state it needs (phases, ids, flags)
 * and the screen sees only what it draws — Orbit's internal and external state. One screen written at
 * three levels keeps three different states behind the same `Store<SignInUi, …>`.
 *
 * [transform] runs on every read, so it must be pure and cheap; per-component mapping stays in `select`.
 */
fun <S, U, I, E> Store<S, I, E>.mapState(transform: (S) -> U): Store<U, I, E> {
    val source = this
    return object : Store<U, I, E> {
        override val state: StateFlow<U> = source.state.mapped(transform)
        override val effects: Flow<E> get() = source.effects
        override val isBusy: StateFlow<Boolean> get() = source.isBusy

        override fun postIntent(intent: I) {
            source.postIntent(intent)
        }
    }
}
