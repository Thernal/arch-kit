package io.thernal.archkit.core.presentation.api.plugin.effect

import kotlinx.coroutines.flow.Flow

/** Delivers effects to the screen, each once. Closed with its ViewModel. */
interface EffectHandler<E : ViewEffect> : AutoCloseable {
    val effects: Flow<BaseEffect>

    fun launchEffect(effect: E)

    fun launchBaseEffect(effect: BaseEffect)
}
