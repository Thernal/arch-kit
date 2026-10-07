package io.thernal.archkit.core.presentation.impl.plugin.effect

import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectFactory
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectHandler
import io.thernal.archkit.core.presentation.api.plugin.effect.ViewEffect
import kotlinx.coroutines.CoroutineScope

class EffectFactoryImpl(
    private val logger: PresentationLogger? = null,
) : EffectFactory {
    override fun <E : ViewEffect> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
    ): EffectHandler<E> {
        return EffectHandlerImpl(owner = owner, logger = logger.takeIf { log })
    }
}
