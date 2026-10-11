package io.thernal.archkit.core.presentation.impl.compose.effect

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.thernal.archkit.core.presentation.api.compose.effect.collectEffects
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import io.thernal.archkit.core.presentation.impl.store.host.StoreHost
import io.thernal.archkit.core.testing.runViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EffectCollectionTest {
    private class Emitting : Logic<Int, String, String> {
        override fun StoreScope<Int, String>.onIntent(intent: String) {
            emit(intent)
        }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun anEffectWaitsWhileTheScreenIsNotResumedAndArrivesWhenItIs() = runViewModelTest {
        val owner = Owner().also { it.registry.currentState = Lifecycle.State.STARTED }
        val store = StoreHost(0, backgroundScope, Emitting())
        val received = mutableListOf<String>()
        backgroundScope.launch {
            store.collectEffects(
                lifecycle = owner.lifecycle,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            ) { effect -> received += effect }
        }
        runCurrent()

        store.postIntent("while stopped")
        runCurrent()
        assertEquals(emptyList(), received)

        owner.registry.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(listOf("while stopped"), received)
    }

    @Test
    fun anEffectEmittedWhileResumedIsHandledAtOnceAndTheBlockMayLaunch() = runViewModelTest {
        val owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
        val store = StoreHost(0, backgroundScope, Emitting())
        val finished = mutableListOf<String>()
        backgroundScope.launch {
            store.collectEffects(
                lifecycle = owner.lifecycle,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            ) { effect ->
                launch {
                    delay(1_000)
                    finished += effect
                }
            }
        }
        runCurrent()

        store.postIntent("first")
        store.postIntent("second")
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("first", "second"), finished)
    }
}
