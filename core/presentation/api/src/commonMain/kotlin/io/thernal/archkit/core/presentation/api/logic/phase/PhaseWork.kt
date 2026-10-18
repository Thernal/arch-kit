package io.thernal.archkit.core.presentation.api.logic.phase

import io.thernal.archkit.core.presentation.api.content.map
import io.thernal.archkit.core.presentation.api.store.observer.StoreTrace
import io.thernal.archkit.core.presentation.api.store.observer.TraceOrigin
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.TracedScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.scope.launchBinder
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Work that belongs to a phase: [block] starts every time the state enters [phase], and is cancelled as
 * soon as the state leaves it. Call it in `onStart`. This is level 3 without a state-machine library —
 * the state is a sealed type of phases, intents are handled per phase, and the work of each phase is a
 * `whileIn` — and it is what Orbit's `runOn` does for intents.
 *
 * The work restarts only on entering the phase, so a phase that updates its own data (a countdown, an
 * upload's progress) keeps its work running. When the work depends on something inside the phase, give
 * it a [key]: it then also restarts when the key changes.
 *
 * Phases come from a `StateFlow`, so a phase left and entered again before the work's coroutine runs is
 * seen as never left.
 *
 * Each run of the work is a chain of its own for the store's observers ([TraceOrigin.Phase]), under the
 * chain whose change it woke up to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <S : Any, T : S, E> StoreScope<S, E>.whileIn(
    phase: KClass<T>,
    key: (T) -> Any? = { },
    block: suspend PhaseScope<S, T, E>.(entered: T) -> Unit,
): Job {
    val store = this
    val tracing = this as? TracedScope<S, E>
    return launchBinder {
        states
            .map { current ->
                @Suppress("UNCHECKED_CAST")
                if (phase.isInstance(current)) {
                    Entered(value = current as T, key = key(current), cause = tracing?.stateTrace)
                } else {
                    null
                }
            }.distinctUntilChanged { old, new ->
                if (old == null || new == null) {
                    old === new
                } else {
                    old.key == new.key
                }
            }.collectLatest { entered ->
                if (entered != null) {
                    val work = store.phaseWork(phase = phase, cause = entered.cause)
                    work.busy {
                        PhaseScope(
                            store = work,
                            phase = phase,
                            entered = entered.value,
                        ).block(entered.value)
                    }
                }
            }
    }
}

/**
 * Runs [block] now if the state is in [phase], and cancels it as soon as the state leaves the phase —
 * Orbit's `runOn`. For work an intent starts that only makes sense in one phase: a resend that the user's
 * going back makes pointless. Null when the state is not in the phase: nothing runs.
 */
fun <S : Any, T : S, E> StoreScope<S, E>.runOn(
    phase: KClass<T>,
    block: suspend PhaseScope<S, T, E>.(current: T) -> Unit,
): Job? {
    val current = state
    if (!phase.isInstance(current)) {
        return null
    }
    @Suppress("UNCHECKED_CAST")
    val entered = current as T
    val store = this
    return launch {
        val work = launch { PhaseScope(store = store, phase = phase, entered = entered).block(entered) }
        val watcher = launch {
            states.first { state -> !phase.isInstance(state) }
            work.cancel()
        }
        work.join()
        watcher.cancel()
    }
}

/** The scope a phase's work runs in: a chain of its own, under the chain whose change entered the phase. */
private fun <S, E> StoreScope<S, E>.phaseWork(
    phase: KClass<*>,
    cause: StoreTrace?,
): StoreScope<S, E> {
    val tracing = this as? TracedScope<S, E> ?: return this
    val trace = tracing.open(origin = TraceOrigin.Phase(phase.simpleName ?: phase.toString()), parent = cause)
    return tracing.traced(trace)
}

private class Entered<T>(
    val value: T,
    val key: Any?,
    val cause: StoreTrace?,
)
