package io.thernal.archkit.core.presentation.api.store.scope

import io.thernal.archkit.core.presentation.api.content.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Collects [flow] while the store lives. This is a binder: a source outside the screen — a session, a
 * setting, an event bus — mapped into the state, with no other logic in it.
 */
fun <T> StoreScope<*, *>.observe(
    flow: Flow<T>,
    onEach: suspend (T) -> Unit,
): Job {
    return launchBinder { flow.collect { value -> onEach(value) } }
}

/**
 * Runs [block] while someone collects the state — the screen is visible — and cancels it
 * [stopTimeoutMillis] after the last collector leaves. It runs again when one comes back. Orbit's
 * `repeatOnSubscription`: for work that only matters on screen, such as live counts, a location, a socket.
 *
 * The timeout keeps a rotation or a quick trip to another app from restarting the work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun StoreScope<*, *>.whileSubscribed(
    stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MILLIS,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    return launchBinder {
        subscribers
            .map { count -> count > 0 }
            .distinctUntilChanged()
            .transformLatest { subscribed ->
                if (!subscribed) {
                    delay(stopTimeoutMillis)
                }
                emit(subscribed)
            }.distinctUntilChanged()
            .collectLatest { subscribed ->
                if (subscribed) {
                    kotlinx.coroutines.coroutineScope(block)
                }
            }
    }
}

/** A binder ([observe]) that runs only while the screen is visible ([whileSubscribed]). */
fun <T> StoreScope<*, *>.observeWhileSubscribed(
    flow: Flow<T>,
    stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MILLIS,
    onEach: suspend (T) -> Unit,
): Job {
    return whileSubscribed(stopTimeoutMillis) { flow.collect { value -> onEach(value) } }
}

const val DEFAULT_STOP_TIMEOUT_MILLIS = 5_000L
