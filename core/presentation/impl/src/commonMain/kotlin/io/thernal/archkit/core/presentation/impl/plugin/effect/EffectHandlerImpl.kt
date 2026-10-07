package io.thernal.archkit.core.presentation.impl.plugin.effect

import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.effect.BaseEffect
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectHandler
import io.thernal.archkit.core.presentation.api.plugin.effect.ViewEffect
import io.thernal.archkit.core.presentation.impl.plugin.PRESENTATION_LOG_TAG
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Effects wait in a buffered channel until the screen collects them, and each goes to one collector,
 * once: a message is not shown twice after a rotation, and one sent while the screen is paused is shown
 * when it resumes.
 */
internal class EffectHandlerImpl<E : ViewEffect>(
    private val owner: String,
    private val logger: PresentationLogger?,
) : EffectHandler<E> {
    private val channel = Channel<BaseEffect>(Channel.BUFFERED)

    override val effects: Flow<BaseEffect> = channel.receiveAsFlow()

    override fun launchEffect(effect: E) {
        launchBaseEffect(effect)
    }

    override fun launchBaseEffect(effect: BaseEffect) {
        logger?.log(tag = PRESENTATION_LOG_TAG, message = "$owner effect · $effect", error = null)
        channel.trySend(effect)
    }

    override fun close() {
        channel.close()
    }
}
