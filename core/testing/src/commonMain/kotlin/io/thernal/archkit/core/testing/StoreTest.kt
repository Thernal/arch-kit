package io.thernal.archkit.core.testing

import io.thernal.archkit.core.presentation.api.keeper.StateKeeper
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.Partial
import io.thernal.archkit.core.presentation.api.store.Start
import io.thernal.archkit.core.presentation.api.store.StoreObserver
import io.thernal.archkit.core.presentation.api.store.StoreSettings
import io.thernal.archkit.core.presentation.impl.store.StoreHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest<S, I, E> internal constructor(
    private val scope: TestScope,
    initialState: S,
    logic: Logic<S, I, E>,
    keeper: StateKeeper<S>?,
) {
    private sealed interface Item {
        data class State(
            val value: Any?,
        ) : Item

        data class Effect(
            val value: Any?,
        ) : Item

        data class Error(
            val error: Throwable,
        ) : Item
    }

    private val items = ArrayDeque<Item>()

    private val host = StoreHost(
        initialState = initialState,
        scope = scope.backgroundScope,
        logic = logic,
        settings = StoreSettings(
            start = Start.Manually,
            onError = { error -> items.addLast(Item.Error(error)) },
            observers = listOf(
                object : StoreObserver {
                    override fun onState(
                        partial: Partial<*>,
                        previous: Any?,
                        state: Any?,
                    ) {
                        items.addLast(Item.State(state))
                    }

                    override fun onEffect(effect: Any?) {
                        items.addLast(Item.Effect(effect))
                    }
                },
            ),
        ),
        keeper = keeper,
    )

    /** The last state the test took — what the next [expectState] reduces from. */
    private var last: S = host.state.value

    /** The store's state now, whatever the test has taken so far. */
    val state: S get() = host.state.value

    /** Runs the logic's `onStart` — needed only with `storeTest(start = false)`. */
    fun start() {
        host.start()
        scope.runCurrent()
    }

    /** Posts [intents] in order, then runs everything that is due. */
    fun send(vararg intents: I) {
        intents.forEach(host::postIntent)
        scope.runCurrent()
    }

    /** Moves time on by [millis] and runs everything that is due by then. */
    fun advance(millis: Long) {
        scope.advanceTimeBy(millis)
        scope.runCurrent()
    }

    /** Collects the state as a visible screen does, until the job is cancelled. */
    fun subscribe(): Job {
        val job = scope.backgroundScope.launch { host.state.collect { } }
        scope.runCurrent()
        return job
    }

    /** The next item is a state equal to the last one taken, changed by [change]. */
    fun expectState(change: S.() -> S) {
        val expected = last.change()
        assertEquals(expected = expected, actual = nextState(), message = "Unexpected state")
    }

    /** The next item is a state; it is returned for assertions that are not equality. */
    fun nextState(): S {
        val item = next("a state")
        val state = assertIs<Item.State>(value = item, message = "Expected a state, got $item").value
        @Suppress("UNCHECKED_CAST")
        return (state as S).also { last = it }
    }

    fun expectEffect(effect: E) {
        assertEquals(expected = effect, actual = nextEffect(), message = "Unexpected effect")
    }

    fun nextEffect(): E {
        val item = next("an effect")
        @Suppress("UNCHECKED_CAST")
        return assertIs<Item.Effect>(value = item, message = "Expected an effect, got $item").value as E
    }

    /** The next item is a failure the store reported. */
    fun expectError(): Throwable {
        val item = next("an error")
        return assertIs<Item.Error>(value = item, message = "Expected an error, got $item").error
    }

    /** Skips [count] items — states, effects or failures — that the test does not care about. */
    fun skip(count: Int = 1) {
        repeat(count) { take() }
    }

    /** Skips everything recorded so far. */
    fun skipAll() {
        while (items.isNotEmpty()) take()
    }

    fun expectNoItems() {
        if (items.isNotEmpty()) {
            fail("Expected nothing more, got ${items.toList()}")
        }
    }

    internal fun finish() {
        val errors = items.filterIsInstance<Item.Error>()
        errors.firstOrNull()?.let { first ->
            throw AssertionError(
                "The store reported a failure the test did not expect",
                first.error,
            )
        }
        if (items.isNotEmpty()) {
            fail("Items the test did not take: ${items.toList()}")
        }
    }

    private fun next(what: String): Item {
        if (items.isEmpty()) {
            fail("Expected $what, got nothing")
        }
        return take()
    }

    private fun take(): Item {
        val item = items.removeFirst()
        @Suppress("UNCHECKED_CAST")
        if (item is Item.State) {
            last = item.value as S
        }
        return item
    }
}
