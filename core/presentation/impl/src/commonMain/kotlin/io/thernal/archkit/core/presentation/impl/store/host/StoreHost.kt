package io.thernal.archkit.core.presentation.impl.store.host

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.Store
import io.thernal.archkit.core.presentation.api.store.StoreOwner
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.state.Partial
import io.thernal.archkit.core.presentation.api.store.state.mapped
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import io.thernal.archkit.core.presentation.impl.store.observer.StoreObserver
import io.thernal.archkit.core.presentation.impl.store.settings.Start
import io.thernal.archkit.core.presentation.impl.store.settings.StoreSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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

/**
 * Hosts a [Logic] on [scope]: owns the state, delivers the effects, and handles every intent on the
 * caller's thread, in order. A ViewModel hosts one through [store] and hands it out as a [StoreOwner]; a
 * test hosts one directly.
 *
 * With a [keeper], the state starts from what the keeper saved before the process died, and the part the
 * keeper keeps is saved again whenever it changes.
 */
class StoreHost<S, I, E>(
    initialState: S,
    scope: CoroutineScope,
    private val logic: Logic<S, I, E>,
    private val settings: StoreSettings = StoreSettings(),
    keeper: StateKeeper<S>? = null,
) : Store<S, I, E> {
    private val mutableState = MutableStateFlow(keeper?.restore(initialState) ?: initialState)
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

    private val storeScope = object : StoreScope<S, E> {
        override val state: S get() = mutableState.value
        override val states: Flow<S> get() = mutableState
        override val subscribers: StateFlow<Int> get() = screens

        override val coroutineScope: CoroutineScope = CoroutineScope(
            scope.coroutineContext +
                SupervisorJob(scope.coroutineContext[Job]) +
                CoroutineExceptionHandler { _, error -> settings.onError(error) },
        )

        override fun reduce(partial: Partial<S>) {
            if (settings.observers.isEmpty()) {
                mutableState.update(partial::reduce)
                return
            }
            var previous = mutableState.value
            var next = previous
            mutableState.update { state ->
                previous = state
                next = partial.reduce(state)
                next
            }
            if (previous != next) {
                notify { onState(partial = partial, previous = previous, state = next) }
            }
        }

        override fun emit(effect: E) {
            notify { onEffect(effect) }
            effectChannel.trySend(effect)
        }

        override suspend fun <T> busy(block: suspend () -> T): T {
            working.update { it + 1 }
            try {
                return block()
            } finally {
                working.update { it - 1 }
            }
        }
    }

    init {
        keeper?.let { kept -> storeScope.coroutineScope.launch { kept.save(states = mutableState) } }
        if (settings.start == Start.Eagerly) {
            start()
        }
    }

    /** Tells every observer; one that fails is reported, and the others still hear. */
    private fun notify(event: StoreObserver.() -> Unit) {
        settings.observers.forEach { observer ->
            try {
                observer.event()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                settings.onError(error)
            }
        }
    }

    /** Runs the logic's `onStart`, once. Needed only with [Start.Manually]; harmless otherwise. */
    fun start() {
        if (isStarted) {
            return
        }
        isStarted = true
        try {
            with(logic) { storeScope.onStart() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            settings.onError(error)
        }
    }

    override fun postIntent(intent: I) {
        startIfLazy()
        notify { onIntent(intent) }
        try {
            with(logic) { storeScope.onIntent(intent) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            settings.onError(error)
        }
    }

    private fun startIfLazy() {
        if (settings.start == Start.Lazily) {
            start()
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
}
