package io.thernal.archkit.core.presentation.api.plugin.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.thernal.archkit.core.presentation.api.plugin.effect.message.LocalMessageEffectHandler
import io.thernal.archkit.core.presentation.api.plugin.effect.message.MessageEffect
import io.thernal.archkit.core.presentation.api.plugin.effect.navigation.LocalNavEffectHandler
import io.thernal.archkit.core.presentation.api.plugin.effect.navigation.NavEffect
import kotlinx.coroutines.CoroutineScope

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
