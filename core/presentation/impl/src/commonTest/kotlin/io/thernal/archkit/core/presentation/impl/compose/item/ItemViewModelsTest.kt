package io.thernal.archkit.core.presentation.impl.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.thernal.archkit.core.presentation.api.compose.item.ItemViewModels
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.StoreOwner
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.store
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import io.thernal.archkit.core.testing.testStoreFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModelsTest {
    /** A component ViewModel whose one intent starts work that ends when [finish] is completed. */
    private class Card(
        val finish: CompletableDeferred<Unit>,
    ) : ViewModel(),
        StoreOwner<Int, Unit, Nothing> {
        var isCleared = false
            private set

        override val store = store(
            factory = testStoreFactory(),
            initialState = 0,
            logic = object : Logic<Int, Unit, Nothing> {
                override fun StoreScope<Int, Nothing>.onIntent(intent: Unit) {
                    launch { finish.await() }
                }
            },
        )

        override fun onCleared() {
            isCleared = true
        }
    }

    private fun item(
        stores: ItemViewModels,
        key: String,
        finish: CompletableDeferred<Unit>,
    ): Card {
        val provider = ViewModelProvider.create(
            store = stores.owner(key).viewModelStore,
            factory = viewModelFactory { initializer { Card(finish) } },
        )
        return provider[Card::class]
    }

    private fun withMain(test: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            test()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun anItemWithNothingRunningIsLetGoWhenItLeavesTheScreen() = withMain {
        val stores = ItemViewModels()
        stores.attach("a")
        val card = item(stores, "a", CompletableDeferred())
        runCurrent()
        assertFalse(card.isCleared)

        stores.detach("a")
        runCurrent()
        assertTrue(card.isCleared)
    }

    @Test
    fun anItemWithWorkInFlightLivesUntilTheWorkEnds() = withMain {
        val stores = ItemViewModels()
        val finish = CompletableDeferred<Unit>()
        stores.attach("a")
        val card = item(stores, "a", finish)
        card.store.postIntent(Unit)
        runCurrent()
        assertTrue(card.store.isBusy.value)

        stores.detach("a")
        runCurrent()
        assertFalse(card.isCleared) // scrolled away, but the request is still going

        finish.complete(Unit)
        runCurrent()
        assertTrue(card.isCleared)
    }

    @Test
    fun anItemThatComesBackWhileBusyKeepsItsViewModel() = withMain {
        val stores = ItemViewModels()
        val finish = CompletableDeferred<Unit>()
        stores.attach("a")
        val card = item(stores, "a", finish)
        card.store.postIntent(Unit)
        runCurrent()
        stores.detach("a")
        runCurrent()

        stores.attach("a") // scrolled back
        finish.complete(Unit)
        runCurrent()
        assertFalse(card.isCleared)
        assertEquals(card, item(stores, "a", finish))
    }

    @Test
    fun anItemThatLeavesTheListIsClearedAtOnceEvenWhileBusy() = withMain {
        val stores = ItemViewModels()
        stores.attach("a")
        val card = item(stores, "a", CompletableDeferred())
        card.store.postIntent(Unit)
        runCurrent()

        stores.retain(emptySet())
        assertTrue(card.isCleared)
    }

    @Test
    fun aBinderIsNotWork() = withMain {
        val stores = ItemViewModels()
        stores.attach("a")
        val card = item(stores, "a", CompletableDeferred())
        runCurrent()
        // Nothing launched: not busy, so it is let go however long an `observe` would have run.
        assertFalse(card.store.isBusy.value)
    }
}
