package io.thernal.archkit.core.presentation.impl.store.factory

import androidx.lifecycle.SavedStateHandle
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.Store
import io.thernal.archkit.core.presentation.api.store.StoreFactory
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper
import io.thernal.archkit.core.presentation.impl.store.host.StoreHost
import io.thernal.archkit.core.presentation.api.store.settings.StoreSettings
import io.thernal.archkit.core.presentation.impl.store.keeper.SavedStateKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer

/** Hosts every store as a [StoreHost] with the app's [settings]. */
class StoreFactoryImpl(
    private val settings: StoreSettings = StoreSettings(),
) : StoreFactory {
    override fun <S, I, E> create(
        scope: CoroutineScope,
        initialState: S,
        logic: Logic<S, I, E>,
        keeper: StateKeeper<S>?,
    ): Store<S, I, E> {
        return StoreHost(
            initialState = initialState,
            scope = scope,
            logic = logic,
            settings = settings,
            keeper = keeper,
        )
    }

    override fun <S, T> keeper(
        handle: SavedStateHandle,
        key: String,
        serializer: KSerializer<T>,
        save: (S) -> T,
        restore: S.(T) -> S,
        saveIntervalMillis: Long,
    ): StateKeeper<S> {
        return SavedStateKeeper(
            handle = handle,
            key = key,
            serializer = serializer,
            save = save,
            restore = restore,
            saveIntervalMillis = saveIntervalMillis,
        )
    }
}
