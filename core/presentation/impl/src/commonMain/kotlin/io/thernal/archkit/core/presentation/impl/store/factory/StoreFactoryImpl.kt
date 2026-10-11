package io.thernal.archkit.core.presentation.impl.store.factory

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.Store
import io.thernal.archkit.core.presentation.api.store.StoreFactory
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper
import io.thernal.archkit.core.presentation.impl.store.host.StoreHost
import io.thernal.archkit.core.presentation.impl.store.settings.StoreSettings
import kotlinx.coroutines.CoroutineScope

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
}
