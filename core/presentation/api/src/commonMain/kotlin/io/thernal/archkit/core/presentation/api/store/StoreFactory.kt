package io.thernal.archkit.core.presentation.api.store

import io.thernal.archkit.core.presentation.api.keeper.StateKeeper
import io.thernal.archkit.core.presentation.api.logic.Logic
import kotlinx.coroutines.CoroutineScope

/**
 * Builds the [Store] a ViewModel owns, with the app's [StoreSettings] — how failures are reported, which
 * observers see what goes through. A ViewModel takes one as a constructor parameter and hosts its logic
 * through [store]; a test takes the one from `core/testing`.
 */
interface StoreFactory {
    fun <S, I, E> create(
        scope: CoroutineScope,
        initialState: S,
        logic: Logic<S, I, E>,
        keeper: StateKeeper<S>? = null,
    ): Store<S, I, E>
}
