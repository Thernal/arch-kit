package io.thernal.archkit.core.presentation.impl.store.host

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.action.ActionLogic
import io.thernal.archkit.core.presentation.api.logic.action.Policy
import io.thernal.archkit.core.presentation.api.logic.child.ChildPartial
import io.thernal.archkit.core.presentation.api.logic.phase.whileIn
import io.thernal.archkit.core.presentation.api.logic.slice.slice
import io.thernal.archkit.core.presentation.api.logic.slot.Hosted
import io.thernal.archkit.core.presentation.api.logic.slot.Slot
import io.thernal.archkit.core.presentation.api.store.observer.StoreEvent
import io.thernal.archkit.core.presentation.api.store.observer.StoreObserver
import io.thernal.archkit.core.presentation.api.store.observer.TraceOrigin
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.scope.setState
import io.thernal.archkit.core.presentation.api.store.settings.Start
import io.thernal.archkit.core.presentation.api.store.settings.StoreSettings
import io.thernal.archkit.core.presentation.api.store.state.Partial
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTracingTest {
    private class Recorder : StoreObserver {
        val events = mutableListOf<StoreEvent>()

        override fun onEvent(event: StoreEvent) {
            events += event
        }

        /** What the chain [origin] started did, in order. */
        fun chain(origin: TraceOrigin): List<String> {
            return events.filter { event -> event.trace.origin == origin }.map { event -> event.line() }
        }

        fun outcomes(action: Any): List<String?> {
            return events
                .filterIsInstance<StoreEvent.Action>()
                .filter { event -> event.action == action }
                .map { event -> event.outcome::class.simpleName }
        }

        private fun StoreEvent.line(): String {
            return when (this) {
                is StoreEvent.Intent -> "intent ${intent ?: "null"}"
                is StoreEvent.Action -> "action $action ${outcome::class.simpleName ?: "?"}"
                is StoreEvent.State -> "state ${state ?: "null"}"
                is StoreEvent.Effect -> "effect ${effect ?: "null"}"
            }
        }
    }

    /** Each intent writes down that it started, and a moment later that it is done. */
    private class Delayed : Logic<List<String>, String, String> {
        override fun StoreScope<List<String>, String>.onIntent(intent: String) {
            setState { this + "$intent started" }
            launch {
                delay(WORK_MILLIS)
                setState { this + "$intent done" }
                emit(intent)
            }
        }
    }

    private class Starting : Logic<Int, Int, Nothing> {
        override fun StoreScope<Int, Nothing>.onStart() {
            setState { 1 }
        }

        override fun StoreScope<Int, Nothing>.onIntent(intent: Int) {
            setState { intent }
        }
    }

    /** Each intent is an action that takes as many milliseconds as it says; a negative one fails. */
    private class Timed(
        private val policy: Policy,
    ) : ActionLogic<Int, Long, Long, Nothing>() {
        override fun StoreScope<Int, Nothing>.toAction(intent: Long) = intent

        override fun policy(action: Long) = policy

        override suspend fun StoreScope<Int, Nothing>.process(action: Long) {
            delay(abs(action))
            require(action >= 0) { "a negative action" }
            setState { this + 1 }
        }
    }

    private data class Bump(
        val by: Int,
    ) : Partial<Int> {
        override fun reduce(state: Int) = state + by
    }

    private class Counter : Logic<Int, Int, String> {
        override fun StoreScope<Int, String>.onIntent(intent: Int) {
            reduce(Bump(intent))
            launch {
                delay(WORK_MILLIS)
                emit("counted $intent")
            }
        }
    }

    private data class Board(
        val moves: Int = 0,
        val counter: Int = 0,
    )

    private class Boarding : Logic<Board, Int, String> {
        private val counter = slice(logic = Counter(), get = Board::counter, set = { copy(counter = it) })

        override fun StoreScope<Board, String>.onStart() = counter.attach(this)

        override fun StoreScope<Board, String>.onIntent(intent: Int) {
            setState { copy(moves = moves + 1) }
            counter.postIntent(intent)
        }
    }

    private class Naming : Logic<String, String, String> {
        override fun StoreScope<String, String>.onIntent(intent: String) {
            launch {
                delay(WORK_MILLIS)
                emit(intent)
            }
        }
    }

    private data class Page(
        val title: String = "untitled",
        val rename: String? = null,
    )

    /** "open" opens the rename dialog, anything else goes to it; its output renames the page and closes it. */
    private class Paging : Logic<Page, String, String> {
        private val rename: Slot<Page, String, String, String, String, String> = Slot(
            get = Page::rename,
            set = { copy(rename = it) },
            create = { title -> Hosted(title, Naming()) },
            onEffect = { name ->
                setState { copy(title = name) }
                rename.close()
                emit("renamed to $name")
            },
        )

        override fun StoreScope<Page, String>.onStart() = rename.attach(this)

        override fun StoreScope<Page, String>.onIntent(intent: String) {
            if (intent == OPEN) {
                rename.open(state.title)
            } else {
                rename.postIntent(intent)
            }
        }
    }

    private sealed interface Upload {
        data object Idle : Upload

        data class Sending(
            val name: String,
        ) : Upload

        data object Sent : Upload
    }

    private class Uploading : Logic<Upload, String, Nothing> {
        override fun StoreScope<Upload, Nothing>.onStart() {
            whileIn(Upload.Sending::class) {
                delay(WORK_MILLIS)
                setState { Upload.Sent }
            }
        }

        override fun StoreScope<Upload, Nothing>.onIntent(intent: String) {
            setState {
                check(intent != BROKEN) { "a partial that fails" }
                Upload.Sending(intent)
            }
        }
    }

    private fun <S, I, E> TestScope.host(
        initialState: S,
        logic: Logic<S, I, E>,
        recorder: Recorder,
        errors: MutableList<Throwable> = mutableListOf(),
    ) = StoreHost(
        initialState = initialState,
        scope = backgroundScope,
        logic = logic,
        settings = StoreSettings(observers = listOf(recorder), onError = { error -> errors += error }),
    )

    @Test
    fun whatAnIntentCausesLaterIsStillInItsChain() = runTest {
        val recorder = Recorder()
        val store = host(initialState = emptyList(), logic = Delayed(), recorder = recorder)

        store.postIntent("a")
        advanceTimeBy(WORK_MILLIS / 2)
        store.postIntent("b")
        advanceTimeBy(WORK_MILLIS * 2)

        assertEquals(
            listOf("intent a", "state [a started]", "state [a started, b started, a done]", "effect a"),
            recorder.chain(TraceOrigin.Intent("a")),
        )
        assertEquals(
            listOf(
                "intent b",
                "state [a started, b started]",
                "state [a started, b started, a done, b done]",
                "effect b",
            ),
            recorder.chain(TraceOrigin.Intent("b")),
        )
    }

    @Test
    fun theStartIsAChainOfItsOwn() = runTest {
        val recorder = Recorder()
        val store = StoreHost(
            initialState = 0,
            scope = backgroundScope,
            logic = Starting(),
            settings = StoreSettings(start = Start.Eagerly, observers = listOf(recorder)),
        )
        store.postIntent(2)

        assertEquals(listOf("state 1"), recorder.chain(TraceOrigin.Start))
        assertEquals(listOf("intent 2", "state 2"), recorder.chain(TraceOrigin.Intent(2)))
    }

    @Test
    fun eachStoreIsNamedAfterItsLogicAndNumbered() = runTest {
        val recorder = Recorder()
        host(initialState = 0, logic = Starting(), recorder = recorder).postIntent(2)
        host(initialState = 0, logic = Starting(), recorder = recorder).postIntent(2)

        val stores = recorder.events.map { event -> event.store }.distinct()
        assertEquals(listOf("Starting", "Starting"), stores.map { store -> store.logic })
        assertEquals(2, stores.map { store -> store.instance }.distinct().size)
    }

    @Test
    fun anActionSaysWhatBecameOfItUnderItsPolicy() = runTest {
        fun outcomes(policy: Policy): List<List<String?>> {
            val recorder = Recorder()
            val store = host(initialState = 0, logic = Timed(policy), recorder = recorder)
            store.postIntent(SLOW)
            advanceTimeBy(FAST)
            store.postIntent(FAST)
            advanceTimeBy(SLOW * 2)
            return listOf(recorder.outcomes(SLOW), recorder.outcomes(FAST))
        }

        assertEquals(
            listOf(listOf("Started", "Finished"), listOf("Started", "Finished")),
            outcomes(Policy.Parallel),
        )
        assertEquals(
            listOf(listOf("Started", "Cancelled"), listOf("Started", "Finished")),
            outcomes(Policy.Latest(KEY)),
        )
        assertEquals(listOf(listOf("Started", "Finished"), listOf("Dropped")), outcomes(Policy.Ignore(KEY)))
        assertEquals(
            listOf(listOf("Started", "Finished"), listOf("Queued", "Started", "Finished")),
            outcomes(Policy.Queue(KEY)),
        )
    }

    @Test
    fun anActionThatFailsSaysSoAndTheFailureStillGoesToOnError() = runTest {
        val recorder = Recorder()
        val errors = mutableListOf<Throwable>()
        val store = host(initialState = 0, logic = Timed(Policy.Parallel), recorder = recorder, errors = errors)

        store.postIntent(-FAST)
        advanceTimeBy(SLOW)

        assertEquals(
            listOf("intent -$FAST", "action -$FAST Started", "action -$FAST Failed"),
            recorder.chain(TraceOrigin.Intent(-FAST)),
        )
        assertEquals(listOf("a negative action"), errors.map { error -> error.message })
    }

    @Test
    fun aSliceWorksInTheChainOfTheIntentThatPostedToIt() = runTest {
        val recorder = Recorder()
        val store = host(initialState = Board(), logic = Boarding(), recorder = recorder)

        store.postIntent(1)
        advanceTimeBy(WORK_MILLIS / 2)
        store.postIntent(2)
        advanceTimeBy(WORK_MILLIS * 2)

        assertEquals(
            listOf(
                "intent 1",
                "state Board(moves=1, counter=0)",
                "intent 1",
                "state Board(moves=1, counter=1)",
                "effect counted 1",
            ),
            recorder.chain(TraceOrigin.Intent(1)),
        )
        assertEquals(
            listOf(Bump(1), Bump(2)),
            recorder.events
                .filterIsInstance<StoreEvent.State>()
                .mapNotNull { event -> (event.partial as? ChildPartial<*, *>)?.partial },
        )
    }

    @Test
    fun aSlotsOutputIsHandledInTheChainOfTheIntentThatCausedIt() = runTest {
        val recorder = Recorder()
        val store = host(initialState = Page(), logic = Paging(), recorder = recorder)

        store.postIntent(OPEN)
        store.postIntent("notes")
        advanceTimeBy(WORK_MILLIS * 2)

        assertEquals(
            listOf("intent $OPEN", "state Page(title=untitled, rename=untitled)"),
            recorder.chain(TraceOrigin.Intent(OPEN)),
        )
        assertEquals(
            listOf(
                "intent notes",
                "intent notes",
                "state Page(title=notes, rename=untitled)",
                "state Page(title=notes, rename=null)",
                "effect renamed to notes",
            ),
            recorder.chain(TraceOrigin.Intent("notes")),
        )
    }

    @Test
    fun phaseWorkIsAChainUnderTheChainThatEnteredThePhase() = runTest {
        val recorder = Recorder()
        val store = host(initialState = Upload.Idle, logic = Uploading(), recorder = recorder)

        store.postIntent("photo")
        advanceTimeBy(WORK_MILLIS * 2)

        val sent = recorder.events
            .filterIsInstance<StoreEvent.State>()
            .single { event -> event.state == Upload.Sent }
        val entering = recorder.events.filterIsInstance<StoreEvent.Intent>().single().trace
        assertEquals(TraceOrigin.Phase("Sending"), sent.trace.origin)
        assertEquals(entering, sent.trace.parent)
    }

    @Test
    fun aPartialThatFailsDoesNotBecomeTheChainThatEnteredThePhase() = runTest {
        val recorder = Recorder()
        val errors = mutableListOf<Throwable>()
        val store = host(initialState = Upload.Idle, logic = Uploading(), recorder = recorder, errors = errors)

        store.postIntent("photo")
        store.postIntent(BROKEN)
        advanceTimeBy(WORK_MILLIS * 2)

        val sent = recorder.events
            .filterIsInstance<StoreEvent.State>()
            .single { event -> event.state == Upload.Sent }
        assertEquals(TraceOrigin.Intent("photo"), sent.trace.parent?.origin)
        assertEquals(listOf("a partial that fails"), errors.map { error -> error.message })
    }
}

private const val WORK_MILLIS = 100L
private const val SLOW = 300L
private const val FAST = 100L
private const val KEY = "work"
private const val OPEN = "open"
private const val BROKEN = "broken"
