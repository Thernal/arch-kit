package io.thernal.archkit.core.presentation.api.store

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.slice.Slice
import io.thernal.archkit.core.presentation.api.logic.slot.Slot

/**
 * Something that owns a [Store] — usually a ViewModel. Named after Android's owners (`LifecycleOwner`,
 * `ViewModelStoreOwner`): it hands out the store, it is not the store. Being an interface, it leaves the
 * superclass free, so an app's own base ViewModel works as it is.
 *
 * The ViewModel holds no behaviour. All of a screen's (or a component's) behaviour is in the [Logic], a
 * plain class: it is tested without a ViewModel, and it can be hosted as a [Slice] or a [Slot] instead.
 *
 * ```kotlin
 * class SearchViewModel(storeFactory: StoreFactory, users: UserRepository) :
 *     ViewModel(),
 *     StoreOwner<SearchState, SearchIntent, SearchEffect> {
 *     override val store = store(storeFactory, SearchState(), SearchLogic(users))
 * }
 * ```
 */
interface StoreOwner<S, I, E> {
    val store: Store<S, I, E>
}
