package io.thernal.archkit.core.presentation.api.plugin.effect

import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.domain.safecall.SafeCallScope
import io.thernal.archkit.core.domain.safecall.runSafeCall
import io.thernal.archkit.core.presentation.api.plugin.PluginContext
import io.thernal.archkit.core.presentation.api.plugin.effect.message.MessageEffect
import io.thernal.archkit.core.presentation.api.string.toUiString

interface EffectPlugin<E : ViewEffect> : PluginContext {
    val effectHandler: EffectHandler<E>

    /** `safeCall` whose failure is shown to the user as an error message. */
    context(_: ViewModel)
    suspend fun <T> safeCall(block: suspend context(SafeCallScope) () -> T): Result<T> {
        return runSafeCall(
            block = block,
            onFailure = { failure -> effectHandler.launchBaseEffect(MessageEffect(failure.toUiString())) },
        )
    }
}
