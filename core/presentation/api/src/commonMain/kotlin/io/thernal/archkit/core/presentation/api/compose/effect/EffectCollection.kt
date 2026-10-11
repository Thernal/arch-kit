package io.thernal.archkit.core.presentation.api.compose.effect

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.thernal.archkit.core.presentation.api.store.Store
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What `CollectEffects` runs, for a caller that has a [Lifecycle] and no composition: collects the store's
 * effects while [lifecycle] is at least [minActiveState], stops when it drops below, and starts again when
 * it comes back. An effect emitted in between waits in the store.
 */
suspend fun <E> Store<*, *, E>.collectEffects(
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.RESUMED,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    onEffect: suspend CoroutineScope.(E) -> Unit,
) {
    lifecycle.repeatOnLifecycle(minActiveState) {
        withContext(dispatcher) {
            val scope = this
            effects.collect { effect -> scope.onEffect(effect) }
        }
    }
}
