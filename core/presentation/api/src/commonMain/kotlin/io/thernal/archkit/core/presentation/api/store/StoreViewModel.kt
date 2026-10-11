package io.thernal.archkit.core.presentation.api.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper

/** Hosts [logic] for as long as this ViewModel lives: its work runs in `viewModelScope`. */
fun <S, I, E> ViewModel.store(
    factory: StoreFactory,
    initialState: S,
    logic: Logic<S, I, E>,
    keeper: StateKeeper<S>? = null,
): Store<S, I, E> {
    return factory.create(
        scope = viewModelScope,
        initialState = initialState,
        logic = logic,
        keeper = keeper,
    )
}
