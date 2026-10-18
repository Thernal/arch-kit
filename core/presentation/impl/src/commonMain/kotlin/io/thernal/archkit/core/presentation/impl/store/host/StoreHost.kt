package io.thernal.archkit.core.presentation.impl.store.host

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.action.Policy
import io.thernal.archkit.core.presentation.api.store.Store
import io.thernal.archkit.core.presentation.api.store.StoreOwner
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper
import io.thernal.archkit.core.presentation.api.store.observer.ActionOutcome
import io.thernal.archkit.core.presentation.api.store.observer.StoreEvent
import io.thernal.archkit.core.presentation.api.store.observer.StoreName
import io.thernal.archkit.core.presentation.api.store.observer.StoreTrace
import io.thernal.archkit.core.presentation.api.store.observer.TraceOrigin
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.TracedScope
import io.thernal.archkit.core.presentation.api.store.settings.Start
import io.thernal.archkit.core.presentation.api.store.settings.StoreSettings
import io.thernal.archkit.core.presentation.api.store.state.Partial
import io.thernal.archkit.core.presentation.api.store.state.mapped
import io.thernal.archkit.core.presentation.impl.store.keeper.SavedStateKeeper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Hosts a [Logic] on [scope]: owns the state, delivers the effects, and handles every intent on the
 * caller's thread, in order. A ViewModel hosts one through [store] and hands it out as a [StoreOwner]; a
 * test hosts one directly.
 *
 * With a [keeper], the state starts from what the keeper saved before the process died, and the part the
 * keeper keeps is saved again whenever it changes.
 *
 * Every intent, and the start, opens a chain of work ([StoreTrace]); the logic gets a scope of that chain,
 * so whatever it reduces and emits, now or from work it launched, reaches the observers as that chain's.
 */
@OptIn(ExperimentalAtomicApi::class)
class StoreHost<S, I, E>(
    initialState: S,
    scope: CoroutineScope,
    private val logic: Logic<S, I, E>,
    private val settings: StoreSettings = StoreSettings(),
    keeper: StateKeeper<S>? = null,
) : Store<S, I, E> {
    private val kept: SavedStateKeeper<S, *>? = keeper?.let { given ->
        given as? SavedStateKeeper<S, *> ?: error("A StateKeeper comes from StoreFactory.keeper")
    }
    private val mutableState = MutableStateFlow(kept?.restore(initialState) ?: initialState)
    private val effectChannel = Channel<E>(Channel.UNLIMITED)
    private val effectCollector = Mutex()
    private var isStarted = false

    /**
     * How many collect the state the way a screen does, through [state]. Not `mutableState.subscriptionCount`:
     * that also counts the store's own collectors (a keeper, `whileIn`, `runOn`), and the work that runs
     * "while the screen is visible" would then never stop.
     */
    private val screens = MutableStateFlow(0)

    private val working = MutableStateFlow(0)

    override val isBusy: StateFlow<Boolean> = working.mapped { count -> count > 0 }

    override val state: StateFlow<S> = StartingStateFlow()

    override val effects: Flow<E> = flow {
        check(effectCollector.tryLock()) {
            "The effects already have a collector. Each effect goes to one collector; collect them in one place."
        }
        try {
            startIfLazy()
            emitAll(effectChannel.receiveAsFlow())
        } finally {
            effectCollector.unlock()
        }
    }

    private val name = StoreName(logic = logic::class.simpleName ?: "Logic", instance = instances.incrementAndFetch())
    private val isObserved = settings.observers.isNotEmpty()
    private val traceIds = AtomicLong(0)

    private var handling: StoreTrace? = null

    /** Kept only while someone observes. */
    private var stateTrace: StoreTrace? = null

    private val coroutineScope = CoroutineScope(
        scope.coroutineContext +
            SupervisorJob(scope.coroutineContext[Job]) +
            CoroutineExceptionHandler { _, error -> settings.onError(error) },
    )

    init {
        kept?.let { saving -> coroutineScope.launch { saving.save(states = mutableState) } }
        if (settings.start == Start.Eagerly) {
            start()
        }
    }

    /** Runs the logic's `onStart`, once. Needed only with [Start.Manually]; harmless otherwise. */
    fun start() {
        if (isStarted) {
            return
        }
        isStarted = true
        handleLogic(open(origin = TraceOrigin.Start, parent = null)) { with(logic) { onStart() } }
    }

    override fun postIntent(intent: I) {
        startIfLazy()
        val trace = open(origin = TraceOrigin.Intent(intent), parent = null)
        notify { StoreEvent.Intent(store = name, trace = trace, intent = intent) }
        handleLogic(trace) { with(logic) { onIntent(intent) } }
    }

    private fun startIfLazy() {
        if (settings.start == Start.Lazily) {
            start()
        }
    }

    /** Runs the logic's part of [trace] on the store's thread; a failure goes to `onError`. */
    private fun handleLogic(
        trace: StoreTrace,
        block: StoreScope<S, E>.() -> Unit,
    ) {
        try {
            handle(trace) { Scope(trace).block() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            settings.onError(error)
        }
    }

    private fun reduce(
        partial: Partial<S>,
        trace: StoreTrace,
    ) {
        if (!isObserved) {
            mutableState.update(partial::reduce)
            return
        }
        // Set before the update: a collector on an immediate dispatcher reads it while the update runs.
        val before = stateTrace
        stateTrace = trace
        var previous = mutableState.value
        var next = previous
        try {
            mutableState.update { state ->
                previous = state
                next = partial.reduce(state)
                next
            }
        } catch (failure: Exception) {
            stateTrace = before
            throw failure
        }
        if (previous == next) {
            stateTrace = before
        } else {
            notify {
                StoreEvent.State(store = name, trace = trace, partial = partial, previous = previous, state = next)
            }
        }
    }

    private fun emit(
        effect: E,
        trace: StoreTrace,
    ) {
        notify { StoreEvent.Effect(store = name, trace = trace, effect = effect) }
        effectChannel.trySend(effect)
    }

    private suspend fun <T> busy(block: suspend () -> T): T {
        working.update { it + 1 }
        try {
            return block()
        } finally {
            working.update { it - 1 }
        }
    }

    private fun open(
        origin: TraceOrigin,
        parent: StoreTrace?,
    ): StoreTrace {
        return StoreTrace(id = traceIds.incrementAndFetch(), origin = origin, parent = parent)
    }

    private fun <T> handle(
        trace: StoreTrace,
        block: () -> T,
    ): T {
        val outer = handling
        handling = trace
        try {
            return block()
        } finally {
            handling = outer
        }
    }

    /** Tells every observer; one that fails is reported, and the others still hear. */
    private fun notify(event: () -> StoreEvent) {
        if (!isObserved) {
            return
        }
        val happened = event()
        settings.observers.forEach { observer ->
            try {
                observer.onEvent(happened)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                settings.onError(error)
            }
        }
    }

    /** The state, which starts a lazy store when someone first collects it. Reading `value` does not. */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private inner class StartingStateFlow : StateFlow<S> {
        override val value: S get() = mutableState.value
        override val replayCache: List<S> get() = mutableState.replayCache

        override suspend fun collect(collector: FlowCollector<S>): Nothing {
            startIfLazy()
            screens.update { it + 1 }
            try {
                mutableState.collect(collector)
            } finally {
                screens.update { it - 1 }
            }
        }
    }

    /** The store as the work of one chain sees it: what it reduces and emits is that chain's. */
    private inner class Scope(
        override val trace: StoreTrace,
    ) : TracedScope<S, E> {
        override val state: S get() = mutableState.value
        override val states: Flow<S> get() = mutableState
        override val subscribers: StateFlow<Int> get() = screens
        override val coroutineScope: CoroutineScope get() = this@StoreHost.coroutineScope
        override val handling: StoreTrace? get() = this@StoreHost.handling
        override val stateTrace: StoreTrace? get() = this@StoreHost.stateTrace

        override fun reduce(partial: Partial<S>) {
            this@StoreHost.reduce(partial = partial, trace = trace)
        }

        override fun emit(effect: E) {
            this@StoreHost.emit(effect = effect, trace = trace)
        }

        override suspend fun <T> busy(block: suspend () -> T): T {
            return this@StoreHost.busy(block)
        }

        override fun open(
            origin: TraceOrigin,
            parent: StoreTrace?,
        ): StoreTrace {
            return this@StoreHost.open(origin = origin, parent = parent)
        }

        override fun traced(trace: StoreTrace): TracedScope<S, E> {
            return Scope(trace)
        }

        override fun <T> handle(
            trace: StoreTrace,
            block: () -> T,
        ): T {
            return this@StoreHost.handle(trace = trace, block = block)
        }

        override fun reportIntent(intent: Any?) {
            notify { StoreEvent.Intent(store = name, trace = trace, intent = intent) }
        }

        override fun reportAction(
            action: Any,
            policy: Policy,
            outcome: ActionOutcome,
        ) {
            notify {
                StoreEvent.Action(store = name, trace = trace, action = action, policy = policy, outcome = outcome)
            }
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private val instances = AtomicInt(0)
