package io.thernal.archkit.core.presentation.api.store

import androidx.lifecycle.SavedStateHandle
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.keeper.DEFAULT_SAVE_INTERVAL_MILLIS
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer

/**
 * Builds the [Store] a ViewModel owns, with the app's settings — how a failure that escapes a logic is reported, which
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

    /**
     * A [StateKeeper] on [handle]: [save] picks the part of the state to keep, [restore] merges it back into
     * the initial state, [serializer] writes it under [key]. A saved value that no longer decodes — the app was
     * updated meanwhile — is dropped. While the state changes, the first change is written at once and the
     * ones that follow within [saveIntervalMillis] are merged into one write of the last value.
     */
    fun <S, T> keeper(
        handle: SavedStateHandle,
        key: String,
        serializer: KSerializer<T>,
        save: (S) -> T,
        restore: S.(T) -> S,
        saveIntervalMillis: Long = DEFAULT_SAVE_INTERVAL_MILLIS,
    ): StateKeeper<S>
}
