package io.thernal.archkit.core.presentation.api.plugin.effect

import io.thernal.archkit.core.presentation.api.plugin.PluginFactory
import kotlinx.coroutines.CoroutineScope

interface EffectFactory : PluginFactory {
    fun <E : ViewEffect> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
    ): EffectHandler<E>
}
