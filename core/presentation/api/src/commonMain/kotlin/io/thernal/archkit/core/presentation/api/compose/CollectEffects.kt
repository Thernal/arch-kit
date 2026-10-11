package io.thernal.archkit.core.presentation.api.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import io.thernal.archkit.core.presentation.api.store.Store
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hands each effect to [onEffect], once. Collected on [dispatcher], `Dispatchers.Main.immediate` by
 * default, so an effect taken from the store is handled before the screen can go away — not dropped
 * between the two. A target that has no Main dispatcher (desktop without `kotlinx-coroutines-swing`) passes
 * its own.
 */
@Composable
fun <E> Store<*, *, E>.CollectEffects(
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    onEffect: suspend (E) -> Unit,
) {
    val currentOnEffect = rememberUpdatedState(onEffect)
    LaunchedEffect(this) {
        withContext(dispatcher) {
            effects.collect { effect -> currentOnEffect.value(effect) }
        }
    }
}
