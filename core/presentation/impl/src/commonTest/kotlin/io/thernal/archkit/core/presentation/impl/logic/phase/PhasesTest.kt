package io.thernal.archkit.core.presentation.impl.logic.phase

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.phase.PhaseScope
import io.thernal.archkit.core.presentation.api.logic.phase.runOn
import io.thernal.archkit.core.presentation.api.logic.phase.whileIn
import io.thernal.archkit.core.presentation.api.store.StoreScope
import io.thernal.archkit.core.presentation.api.store.launch
import io.thernal.archkit.core.presentation.api.store.setState
import io.thernal.archkit.core.presentation.impl.store.StoreHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PhasesTest {
    private sealed interface Phase {
        data object Idle : Phase

        data class Counting(
            val count: Int = 0,
            val round: Int = 0,
        ) : Phase

        data class Done(
            val count: Int,
        ) : Phase
    }

    private enum class Command { Count, Stop, NextRound, Ping }

    /** Counts once a second while Counting; Ping is work that only makes sense while counting. */
    private class Counter(
        private val pings: MutableList<Int>,
        private val starts: MutableList<Int>,
    ) : Logic<Phase, Command, String> {
        override fun StoreScope<Phase, String>.onStart() {
            whileIn(Phase.Counting::class, key = { it.round }) { entered ->
                starts += entered.round
                while (true) {
                    delay(1_000)
                    setState { copy(count = count + 1) }
                    if (state.count == 3) {
                        emit("three")
                    }
                }
            }
        }

        override fun StoreScope<Phase, String>.onIntent(intent: Command) {
            when (val phase = state) {
                Phase.Idle -> if (intent == Command.Count) {
                    setState { Phase.Counting() }
                }

                is Phase.Counting -> when (intent) {
                    Command.Stop -> setState { Phase.Done(phase.count) }

                    Command.NextRound -> setState { phase.copy(round = phase.round + 1) }

                    Command.Ping -> runOn(Phase.Counting::class) { current ->
                        delay(2_000)
                        pings += current.count
                    }

                    Command.Count -> Unit
                }

                is Phase.Done -> if (intent == Command.Count) {
                    setState { Phase.Counting() }
                }
            }
        }
    }

    @Test
    fun workRunsWhileInThePhaseAndSurvivesThePhaseUpdatingItself() = runTest {
        val starts = mutableListOf<Int>()
        val store = StoreHost<Phase, Command, String>(Phase.Idle, backgroundScope, Counter(mutableListOf(), starts))
        store.postIntent(Command.Count)
        runCurrent()
        advanceTimeBy(3_500)
        assertEquals(Phase.Counting(count = 3), store.state.value)
        assertEquals(listOf(0), starts)

        store.postIntent(Command.Stop)
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(Phase.Done(3), store.state.value)

        // Entering the phase again starts the work again.
        store.postIntent(Command.Count)
        runCurrent()
        advanceTimeBy(1_500)
        assertEquals(Phase.Counting(count = 1), store.state.value)
        assertEquals(listOf(0, 0), starts)
    }

    @Test
    fun aKeyChangeRestartsTheWork() = runTest {
        val starts = mutableListOf<Int>()
        val store = StoreHost<Phase, Command, String>(Phase.Idle, backgroundScope, Counter(mutableListOf(), starts))
        store.postIntent(Command.Count)
        runCurrent()
        advanceTimeBy(1_500)
        store.postIntent(Command.NextRound)
        runCurrent()
        // The restarted work waits a full second again: 1.5 s + 0.9 s gives no second tick.
        advanceTimeBy(900)
        assertEquals(Phase.Counting(count = 1, round = 1), store.state.value)
        assertEquals(listOf(0, 1), starts)
    }

    @Test
    fun runOnIsCancelledWhenThePhaseIsLeftAndDoesNothingOutsideIt() = runTest {
        val pings = mutableListOf<Int>()
        val store = StoreHost<Phase, Command, String>(Phase.Idle, backgroundScope, Counter(pings, mutableListOf()))
        store.postIntent(Command.Count)
        store.postIntent(Command.Ping)
        runCurrent()
        advanceTimeBy(2_500)
        assertEquals(listOf(0), pings)

        store.postIntent(Command.Ping)
        runCurrent()
        advanceTimeBy(500)
        store.postIntent(Command.Stop)
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(listOf(0), pings)

        val outside =
            StoreHost<Phase, Command, String>(
                Phase.Idle,
                backgroundScope,
                object : Logic<Phase, Command, String> {
                    override fun StoreScope<Phase, String>.onIntent(intent: Command) {
                        assertNull(runOn(Phase.Counting::class) { error("must not run") })
                    }
                },
            )
        outside.postIntent(Command.Ping)
    }

    @Test
    fun aLateResultCannotOverwriteThePhaseTheUserMovedTo() = runTest {
        val logic = object : Logic<Phase, Command, String> {
            override fun StoreScope<Phase, String>.onIntent(intent: Command) {
                if (intent == Command.Count) {
                    setState { Phase.Counting() }
                    launch {
                        // Not phase-bound work: its scope outlives the phase.
                        val phase = PhaseScope(
                            store = this@onIntent,
                            phase = Phase.Counting::class,
                            entered = Phase.Counting(),
                        )
                        delay(1_000)
                        phase.setState { Phase.Done(99) }
                    }
                } else {
                    setState { Phase.Idle }
                }
            }
        }
        val store = StoreHost<Phase, Command, String>(Phase.Idle, backgroundScope, logic)
        store.postIntent(Command.Count)
        store.postIntent(Command.Stop)
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(Phase.Idle, store.state.value)
    }
}
