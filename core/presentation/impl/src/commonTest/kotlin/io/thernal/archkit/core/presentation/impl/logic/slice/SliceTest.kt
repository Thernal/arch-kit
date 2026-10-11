package io.thernal.archkit.core.presentation.impl.logic.slice

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.slice.Slice
import io.thernal.archkit.core.presentation.api.logic.slice.slice
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.scope.setState
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import io.thernal.archkit.core.presentation.impl.store.host.StoreHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SliceTest {
    private class Counter : Logic<Int, Unit, String> {
        override fun StoreScope<Int, String>.onIntent(intent: Unit) {
            setState { this + 1 }
            if (state == 3) {
                emit("three")
            }
        }
    }

    private data class Parent(
        val left: Int = 0,
        val right: Int = 0,
    )

    private class Pair : Logic<Parent, Boolean, String> {
        private val left = slice(Counter(), get = Parent::left, set = { copy(left = it) })
        private val right = Slice(
            Counter(),
            get = Parent::right,
            set = { copy(right = it) },
            onEffect = { effect -> emit("right: $effect") },
        )

        override fun StoreScope<Parent, String>.onStart() {
            left.attach(this)
            right.attach(this)
        }

        override fun StoreScope<Parent, String>.onIntent(intent: Boolean) {
            if (intent) {
                left.postIntent(Unit)
            } else {
                right.postIntent(Unit)
            }
        }
    }

    @Test
    fun eachSliceChangesOnlyItsPartAndItsEffectsGoOutThroughTheParent() = runTest(UnconfinedTestDispatcher()) {
        val store = StoreHost(Parent(), backgroundScope, Pair())
        val effects = mutableListOf<String>()
        backgroundScope.launch { store.effects.toList(effects) }

        repeat(3) { store.postIntent(false) }
        store.postIntent(true)

        assertEquals(Parent(left = 1, right = 3), store.state.value)
        assertEquals(listOf("right: three"), effects)
    }

    @Test
    fun theSameLogicRunsAsAStoreOfItsOwn() = runTest(UnconfinedTestDispatcher()) {
        val store = StoreHost(0, backgroundScope, Counter())
        repeat(2) { store.postIntent(Unit) }
        assertEquals(2, store.state.value)
    }
}
