package io.thernal.archkit.core.presentation.impl.store.host

import androidx.lifecycle.SavedStateHandle
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.phase.whileIn
import io.thernal.archkit.core.presentation.api.store.keeper.keeper
import io.thernal.archkit.core.presentation.api.store.scope.DEFAULT_STOP_TIMEOUT_MILLIS
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.scope.observeWhileSubscribed
import io.thernal.archkit.core.presentation.api.store.scope.setState
import io.thernal.archkit.core.presentation.api.store.scope.whileSubscribed
import io.thernal.archkit.core.presentation.api.store.state.Partial
import io.thernal.archkit.core.presentation.api.store.state.mapState
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import io.thernal.archkit.core.presentation.impl.store.observer.StoreObserver
import io.thernal.archkit.core.presentation.impl.store.settings.Start
import io.thernal.archkit.core.presentation.impl.store.settings.StoreSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoreHostTest {
    private class Failing : Logic<Int, String, Nothing> {
        override fun StoreScope<Int, Nothing>.onIntent(intent: String) {
            when (intent) {
                "throw" -> error("in the intent")
                "fail later" -> launch { error("in launched work") }
                else -> setState { this + 1 }
            }
        }
    }

    private class Echo(
        private val starts: MutableList<Unit> = mutableListOf(),
    ) : Logic<Int, Int, Int> {
        override fun StoreScope<Int, Int>.onStart() {
            starts += Unit
        }

        override fun StoreScope<Int, Int>.onIntent(intent: Int) {
            reduce(Add(intent))
            emit(intent)
        }
    }

    private data class Add(
        val amount: Int,
    ) : Partial<Int> {
        override fun reduce(state: Int) = state + amount
    }

    @Test
    fun aFailureIsReportedAndTheStoreKeepsWorking() = runTest {
        val errors = mutableListOf<String?>()
        val store = StoreHost(0, backgroundScope, Failing(), StoreSettings(onError = { errors += it.message }))

        store.postIntent("throw")
        store.postIntent("fail later")
        advanceTimeBy(1_000)
        store.postIntent("count")
        advanceTimeBy(1_000)

        assertEquals<List<String?>>(listOf("in the intent", "in launched work"), errors)
        assertEquals<Int>(1, store.state.value)
    }

    @Test
    fun effectsEmittedWithNoCollectorWaitForOne() = runTest {
        val store = StoreHost(0, backgroundScope, Echo())
        store.postIntent(1)
        store.postIntent(2)
        val effects = mutableListOf<Int>()
        backgroundScope.launch { store.effects.toList(effects) }
        runCurrent()
        assertEquals(listOf(1, 2), effects)
    }

    @Test
    fun aSecondEffectCollectorFailsAtOnce() = runTest {
        val store = StoreHost(0, backgroundScope, Echo())
        backgroundScope.launch { store.effects.collect { } }
        runCurrent()
        assertFailsWith<IllegalStateException> { store.effects.first() }
    }

    @Test
    fun aLazyStoreStartsOnFirstUseAndAManualOneOnlyWhenAsked() = runTest {
        val lazyStarts = mutableListOf<Unit>()
        val lazy = StoreHost(0, backgroundScope, Echo(lazyStarts))
        assertEquals(0, lazy.state.value)
        assertEquals(0, lazyStarts.size)
        backgroundScope.launch { lazy.state.collect { } }
        runCurrent()
        assertEquals(1, lazyStarts.size)

        val manualStarts = mutableListOf<Unit>()
        val manual = StoreHost(0, backgroundScope, Echo(manualStarts), StoreSettings(start = Start.Manually))
        manual.postIntent(1)
        assertEquals(0, manualStarts.size)
        manual.start()
        manual.start()
        assertEquals(1, manualStarts.size)
    }

    @Test
    fun theObserverSeesIntentsNamedPartialsAndEffectsInOrder() = runTest {
        val log = mutableListOf<String>()
        val observer = object : StoreObserver {
            override fun onIntent(intent: Any?) {
                log += "intent ${intent ?: "null"}"
            }

            override fun onState(
                partial: Partial<*>,
                previous: Any?,
                state: Any?,
            ) {
                log += "$partial: ${previous ?: "null"} -> ${state ?: "null"}"
            }

            override fun onEffect(effect: Any?) {
                log += "effect ${effect ?: "null"}"
            }
        }
        val store = StoreHost(0, backgroundScope, Echo(), StoreSettings(observers = listOf(observer)))
        store.postIntent(2)
        store.postIntent(0)
        assertEquals(
            listOf("intent 2", "Add(amount=2): 0 -> 2", "effect 2", "intent 0", "effect 0"),
            log,
        )
    }

    @Test
    fun theMappedStoreShowsOnlyTheScreensState() = runTest {
        val store = StoreHost(0, backgroundScope, Echo()).mapState { count -> "count: $count" }
        val seen = mutableListOf<String>()
        backgroundScope.launch { store.state.toList(seen) }
        runCurrent()
        store.postIntent(1)
        runCurrent()
        assertEquals("count: 1", store.state.value)
        assertEquals(listOf("count: 0", "count: 1"), seen)
    }

    @Test
    fun whileSubscribedWorkRunsOnlyWhileTheStateIsCollected() = runTest {
        val source = MutableSharedFlow<Int>()
        val logic = object : Logic<Int, Unit, Nothing> {
            override fun StoreScope<Int, Nothing>.onStart() {
                observeWhileSubscribed(source, stopTimeoutMillis = 1_000) { value -> setState { value } }
            }

            override fun StoreScope<Int, Nothing>.onIntent(intent: Unit) = Unit
        }
        val store = StoreHost(0, backgroundScope, logic, StoreSettings(start = Start.Eagerly))
        runCurrent()
        assertEquals(0, source.subscriptionCount.value)

        val screen = backgroundScope.launch { store.state.collect { } }
        runCurrent()
        source.emit(1)
        assertEquals(1, store.state.value)

        screen.cancel()
        runCurrent()
        assertTrue(source.subscriptionCount.value > 0, "still running within the stop timeout")
        advanceTimeBy(1_500)
        assertEquals(0, source.subscriptionCount.value)
    }

    private class Ticking : Logic<Int, Unit, Nothing> {
        var ticks = 0

        override fun StoreScope<Int, Nothing>.onStart() {
            whileSubscribed {
                while (true) {
                    delay(1_000)
                    ticks++
                }
            }
            // The store's own collectors, which are not a screen.
            whileIn(Int::class) { }
        }

        override fun StoreScope<Int, Nothing>.onIntent(intent: Unit) = Unit
    }

    @Test
    fun theStoresOwnCollectorsAreNotAScreen() = runTest {
        val handle = SavedStateHandle()
        val keeper = handle.keeper<Int, Int>("count", Int.serializer(), save = { it }, restore = { this })
        val logic = Ticking()
        val store = StoreHost(0, backgroundScope, logic, keeper = keeper)
        store.postIntent(Unit)
        runCurrent()

        // A keeper and a `whileIn` collect the state, and nobody looks at the screen: nothing may run.
        advanceTimeBy(20_000)
        assertEquals(0, logic.ticks)

        val screen = backgroundScope.launch { store.state.collect { } }
        runCurrent()
        advanceTimeBy(3_500)
        assertEquals(3, logic.ticks)

        // Scrolled away: it goes on through the stop timeout, then stops for good.
        screen.cancel()
        advanceTimeBy(DEFAULT_STOP_TIMEOUT_MILLIS + 1_000)
        val stopped = logic.ticks
        advanceTimeBy(20_000)
        assertEquals(stopped, logic.ticks)
    }

    @Test
    fun everyObserverHearsInOrderAndOneThatFailsDoesNotSilenceTheOthers() = runTest {
        val log = mutableListOf<String>()
        val errors = mutableListOf<String?>()
        fun observer(
            name: String,
            fails: Boolean = false,
        ) = object : StoreObserver {
            override fun onIntent(intent: Any?) {
                log += "$name ${intent ?: "null"}"
                if (fails) {
                    error("$name failed")
                }
            }
        }
        val settings = StoreSettings(
            onError = { errors += it.message },
            observers = listOf(observer("log"), observer("analytics", fails = true), observer("debug")),
        )
        StoreHost(0, backgroundScope, Echo(), settings).postIntent(1)
        assertEquals(listOf("log 1", "analytics 1", "debug 1"), log)
        assertEquals<List<String?>>(listOf("analytics failed"), errors)
    }

    @Test
    fun byDefaultAFailureFailsFast() = runTest {
        val store = StoreHost(0, backgroundScope, Failing())
        assertFailsWith<IllegalStateException> { store.postIntent("throw") }
    }

    /** A guard reads a flag that the intent wrote in its synchronous part, before it launched anything. */
    private class Guarded : Logic<Boolean, Unit, Nothing> {
        var requests = 0

        override fun StoreScope<Boolean, Nothing>.onIntent(intent: Unit) {
            if (state) {
                return
            }
            setState { true }
            launch {
                requests++
                delay(1_000)
                setState { false }
            }
        }
    }

    @Test
    fun aFlagWrittenInTheSynchronousPartGuardsTheSecondTapOnAnyScope() = runTest {
        val logic = Guarded()
        // backgroundScope runs on a test dispatcher: launched work is queued until runCurrent().
        val store = StoreHost(false, backgroundScope, logic)
        store.postIntent(Unit)
        store.postIntent(Unit)
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(1, logic.requests)
    }
}
