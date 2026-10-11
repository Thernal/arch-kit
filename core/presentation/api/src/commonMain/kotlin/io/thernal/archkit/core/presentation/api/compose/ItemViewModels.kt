package io.thernal.archkit.core.presentation.api.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.presentation.api.store.Store
import io.thernal.archkit.core.presentation.api.store.StoreOwner
import io.thernal.archkit.core.presentation.api.store.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * A ViewModel store per list item, for component ViewModels. An item's ViewModels live while the item is
 * on screen, and after it has scrolled away for as long as their work runs: a like that is being sent is
 * not cancelled by a scroll, and a ViewModel that has nothing running is let go. The ones kept are the
 * items on screen and those with work in flight, so the number does not grow with the list.
 *
 * "Work" is what [Store.isBusy] says: launched work, actions, the work of a phase. A ViewModel's state is
 * gone with it, so what a finished piece of work produced belongs in the list's data source (a repository
 * or a cache) before it ends; the item reads it from there when it comes back.
 *
 * An item that leaves the list is cleared at once, working or not.
 */
class ItemViewModels : ViewModel() {
    private class Entry(
        val owner: ViewModelStoreOwner,
    ) {
        var onScreen = 0
        var release: Job? = null
    }

    private val entries = mutableMapOf<Any, Entry>()

    fun owner(key: Any): ViewModelStoreOwner = entry(key).owner

    private fun entry(key: Any): Entry {
        return entries.getOrPut(key) {
            Entry(
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                },
            )
        }
    }

    /** The item is composed: nothing releases it now. */
    fun attach(key: Any) {
        val entry = entry(key)
        entry.onScreen++
        entry.release?.cancel()
        entry.release = null
    }

    /** The item left the composition: it is released as soon as none of its ViewModels is busy. */
    fun detach(key: Any) {
        val entry = entries[key] ?: return
        entry.onScreen--
        if (entry.onScreen > 0) {
            return
        }
        entry.release = viewModelScope.launch {
            val busy = busyOf(entry.owner)
            if (busy.isNotEmpty()) {
                combine(busy) { states -> states.any { it } }.first { isBusy -> !isBusy }
            }
            if (entry.onScreen == 0) {
                clear(key)
            }
        }
    }

    private fun busyOf(owner: ViewModelStoreOwner): List<StateFlow<Boolean>> {
        val store = owner.viewModelStore
        return store.keys().mapNotNull { name -> (store[name] as? StoreOwner<*, *, *>)?.store?.isBusy }
    }

    /** Clears the ViewModels of the items that are no longer in [keys], busy or not. */
    fun retain(keys: Set<Any>) {
        (entries.keys - keys).forEach(::clear)
    }

    private fun clear(key: Any) {
        val entry = entries.remove(key) ?: return
        entry.release?.cancel()
        entry.owner.viewModelStore.clear()
    }

    override fun onCleared() {
        entries.keys.toList().forEach(::clear)
    }
}
