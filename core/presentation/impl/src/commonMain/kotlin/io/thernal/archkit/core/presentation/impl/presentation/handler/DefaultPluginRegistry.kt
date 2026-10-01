package io.thernal.archkit.core.presentation.impl.presentation.handler

import io.thernal.archkit.core.event.api.domain.EventBus
import io.thernal.archkit.core.presentation.api.presentation.effect.EffectFactory
import io.thernal.archkit.core.presentation.api.presentation.event.EventFactory
import io.thernal.archkit.core.presentation.api.presentation.intent.IntentFactory
import io.thernal.archkit.core.presentation.api.presentation.plugin.PluginFactory
import io.thernal.archkit.core.presentation.api.presentation.plugin.PluginRegistry
import io.thernal.archkit.core.presentation.api.presentation.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.presentation.state.StateFactory
import kotlin.reflect.KClass

/** [PluginRegistry] over a map of factories, keyed by the factory contract they implement. */
class DefaultPluginRegistry(
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
            return DefaultPluginRegistry(
                mapOf(
                    StateFactory::class to DefaultStateFactory(logger),
                    IntentFactory::class to DefaultIntentFactory(logger),
                    EffectFactory::class to DefaultEffectFactory(logger),
                    EventFactory::class to DefaultEventFactory(eventBus = eventBus, logger = logger),
                ),
            )
        }
    }
}
