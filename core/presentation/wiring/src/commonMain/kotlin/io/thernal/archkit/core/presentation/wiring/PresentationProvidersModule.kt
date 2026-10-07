package io.thernal.archkit.core.presentation.wiring

import androidx.compose.runtime.ProvidedValue
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import io.thernal.archkit.core.event.api.domain.EventBus
import io.thernal.archkit.core.presentation.api.plugin.PluginFactory
import io.thernal.archkit.core.presentation.api.plugin.PluginRegistry
import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectFactory
import io.thernal.archkit.core.presentation.api.plugin.event.EventFactory
import io.thernal.archkit.core.presentation.api.plugin.intent.IntentFactory
import io.thernal.archkit.core.presentation.api.plugin.state.StateFactory
import io.thernal.archkit.core.presentation.api.viewmodel.factory.LocalViewModelResolver
import io.thernal.archkit.core.presentation.impl.plugin.PluginRegistryImpl
import io.thernal.archkit.core.presentation.impl.plugin.effect.EffectFactoryImpl
import io.thernal.archkit.core.presentation.impl.plugin.event.EventFactoryImpl
import io.thernal.archkit.core.presentation.impl.plugin.intent.IntentFactoryImpl
import io.thernal.archkit.core.presentation.impl.plugin.state.StateFactoryImpl
import io.thernal.archkit.core.presentation.impl.viewmodel.factory.AppViewModelFactory
import io.thernal.archkit.core.presentation.impl.viewmodel.factory.MetroViewModelResolver
import kotlin.reflect.KClass

private typealias ManualFactories =
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>

/**
 * The plugin registry every ViewModel reads (`override val plugins: PluginRegistry`), its four
 * factories, the app's [MetroViewModelFactory], and the ViewModel factory renderer as a composition
 * local. The app graph implements Metro's `ViewModelGraph` (the ViewModel maps) and installs
 * `ContributedLocals` and `LocalMetroViewModelFactory` at the root.
 *
 * A new plugin contributes its own `@IntoMap @ClassKey` factory from its own wiring; loggers are
 * contributed into `Set<PresentationLogger>`.
 */
@BindingContainer
@ContributesTo(AppScope::class)
interface PresentationProvidersModule {
    @Multibinds(allowEmpty = true)
    val loggers: Set<PresentationLogger>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun providePluginRegistry(factories: Map<KClass<*>, PluginFactory>): PluginRegistry {
            return PluginRegistryImpl(factories)
        }

        @Provides
        @IntoMap
        @ClassKey(StateFactory::class)
        fun provideStateFactory(loggers: Set<PresentationLogger>): PluginFactory {
            return StateFactoryImpl(loggers.combined())
        }

        @Provides
        @IntoMap
        @ClassKey(IntentFactory::class)
        fun provideIntentFactory(loggers: Set<PresentationLogger>): PluginFactory {
            return IntentFactoryImpl(loggers.combined())
        }

        @Provides
        @IntoMap
        @ClassKey(EffectFactory::class)
        fun provideEffectFactory(loggers: Set<PresentationLogger>): PluginFactory {
            return EffectFactoryImpl(loggers.combined())
        }

        @Provides
        @IntoMap
        @ClassKey(EventFactory::class)
        fun provideEventFactory(
            eventBus: EventBus,
            loggers: Set<PresentationLogger>,
        ): PluginFactory {
            return EventFactoryImpl(eventBus = eventBus, logger = loggers.combined())
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideViewModelFactory(
            viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
            assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
            manualAssistedFactoryProviders: ManualFactories,
        ): MetroViewModelFactory {
            return AppViewModelFactory(
                viewModelProviders = viewModelProviders,
                assistedFactoryProviders = assistedFactoryProviders,
                manualAssistedFactoryProviders = manualAssistedFactoryProviders,
            )
        }

        @Provides
        @IntoSet
        fun provideViewModelResolver(): ProvidedValue<*> {
            return LocalViewModelResolver provides MetroViewModelResolver
        }

        private fun Set<PresentationLogger>.combined(): PresentationLogger? {
            if (isEmpty()) {
                return null
            }
            return PresentationLogger { tag, message, error ->
                forEach { logger ->
                    logger.log(
                        tag = tag,
                        message = message,
                        error = error,
                    )
                }
            }
        }
    }
}
