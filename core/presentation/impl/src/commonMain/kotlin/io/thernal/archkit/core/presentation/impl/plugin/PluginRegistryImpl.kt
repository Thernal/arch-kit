package io.thernal.archkit.core.presentation.impl.plugin

import io.thernal.archkit.core.event.api.domain.EventBus
import io.thernal.archkit.core.presentation.api.plugin.PluginFactory
import io.thernal.archkit.core.presentation.api.plugin.PluginRegistry
import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectFactory
import io.thernal.archkit.core.presentation.api.plugin.event.EventFactory
import io.thernal.archkit.core.presentation.api.plugin.intent.IntentFactory
import io.thernal.archkit.core.presentation.api.plugin.state.StateFactory
import io.thernal.archkit.core.presentation.impl.plugin.effect.EffectFactoryImpl
import io.thernal.archkit.core.presentation.impl.plugin.event.EventFactoryImpl
import io.thernal.archkit.core.presentation.impl.plugin.intent.IntentFactoryImpl
import io.thernal.archkit.core.presentation.impl.plugin.state.StateFactoryImpl
import kotlin.reflect.KClass

/** [PluginRegistry] over a map of factories, keyed by the factory contract they implement. */
class PluginRegistryImpl(
    private val factories: Map<KClass<*>, PluginFactory>,
) : PluginRegistry {
    @Suppress("UNCHECKED_CAST") // Keyed by the contract the value implements.
    override fun <T : PluginFactory> factory(key: KClass<T>): T {
        val factory = factories[key]
            ?: error("No ${key.simpleName ?: key} in the plugin registry; contribute it with @IntoMap @ClassKey")
        return factory as T
    }

    companion object {
        /** The four built-in factories, without DI — for a hand-wired app or a test. */
        fun of(
            eventBus: EventBus,
            logger: PresentationLogger? = null,
        ): PluginRegistry {
            return PluginRegistryImpl(
                mapOf(
                    StateFactory::class to StateFactoryImpl(logger),
                    IntentFactory::class to IntentFactoryImpl(logger),
                    EffectFactory::class to EffectFactoryImpl(logger),
                    EventFactory::class to EventFactoryImpl(eventBus = eventBus, logger = logger),
                ),
            )
        }
    }
}
