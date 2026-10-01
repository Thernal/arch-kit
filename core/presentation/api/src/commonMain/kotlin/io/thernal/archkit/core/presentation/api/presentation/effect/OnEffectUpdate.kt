package io.thernal.archkit.core.presentation.api.presentation.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.thernal.archkit.core.presentation.api.presentation.model.BaseEffect
import io.thernal.archkit.core.presentation.api.presentation.model.MessageEffect
import io.thernal.archkit.core.presentation.api.presentation.model.NavEffect
import io.thernal.archkit.core.presentation.api.presentation.model.ViewEffect
import kotlinx.coroutines.CoroutineScope

/** Shows a [MessageEffect] — the app's snackbar or toast. Installed at the root. */
fun interface MessageEffectHandler {
    suspend fun show(effect: MessageEffect)
}

/** Runs a [NavEffect] on the app's navigator. Installed at the root, or wherever the navigator is. */
fun interface NavEffectHandler {
    fun run(effect: NavEffect<*>)
}

/** By default messages are dropped — an isolated preview needs no snackbar host. */
val LocalMessageEffectHandler = staticCompositionLocalOf { MessageEffectHandler { } }

/** By default navigation is dropped — an isolated preview has no navigator. */
val LocalNavEffectHandler = staticCompositionLocalOf { NavEffectHandler { } }

/**
 * Collects the ViewModel's effects while the screen is resumed: messages go to the
 * [LocalMessageEffectHandler], navigation to the [LocalNavEffectHandler]. `createViewModel()` calls it
 * already.
 */
@Composable
fun EffectPlugin<*>.OnEffectUpdate() {
    CollectEffects(handler = effectHandler, own = null)
}

/** [OnEffectUpdate], also handing the feature's own effects [E] to [collector]. */
@Composable
inline fun <reified E : ViewEffect> EffectPlugin<E>.OnEffectUpdate(
    noinline collector: suspend CoroutineScope.(E) -> Unit,
) {
    CollectEffects(handler = effectHandler) { effect ->
        if (effect is E) {
            collector(effect)
        }
    }
}

/** What both [OnEffectUpdate]s run; [own] receives every effect that is neither a message nor navigation. */
@Composable
fun CollectEffects(
    handler: EffectHandler<*>,
    own: (suspend CoroutineScope.(BaseEffect) -> Unit)?,
) {
    val owner = LocalLifecycleOwner.current
    val messages by rememberUpdatedState(LocalMessageEffectHandler.current)
    val navigation by rememberUpdatedState(LocalNavEffectHandler.current)
    val current by rememberUpdatedState(own)
    LaunchedEffect(key1 = handler, key2 = owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            handler.effects.collect { effect ->
                when (effect) {
                    is MessageEffect -> messages.show(effect)
                    is NavEffect<*> -> navigation.run(effect)
                    else -> current?.invoke(this, effect)
                }
            }
        }
    }
}
