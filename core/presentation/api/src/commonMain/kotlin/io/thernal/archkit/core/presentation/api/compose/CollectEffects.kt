package io.thernal.archkit.core.presentation.api.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.thernal.archkit.core.presentation.api.store.Store
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Hands each effect to [onEffect], once, while the screen is at least [minActiveState] — `RESUMED` by
 * default, so a screen in the background does not navigate or show a message nobody sees. An effect emitted
 * meanwhile waits in the store and is handled when the screen comes back.
 *
 * [onEffect] runs in a [CoroutineScope], so it can suspend (a snackbar that waits for the user holds the
 * next effect back — a queue) or `launch` (the next effect does not wait). It is collected on
 * [dispatcher], `Dispatchers.Main.immediate` by default, so an effect taken from the store is handled
 * before the screen can go away. A target that has no Main dispatcher (desktop without
 * `kotlinx-coroutines-swing`) passes its own.
 */
@Composable
fun <E> Store<*, *, E>.CollectEffects(
    minActiveState: Lifecycle.State = Lifecycle.State.RESUMED,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    onEffect: suspend CoroutineScope.(E) -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val currentOnEffect = rememberUpdatedState(onEffect)
    LaunchedEffect(key1 = this, key2 = owner) {
        collectEffects(
            lifecycle = owner.lifecycle,
            minActiveState = minActiveState,
            dispatcher = dispatcher,
        ) { effect -> currentOnEffect.value(this, effect) }
    }
}
