package io.thernal.archkit.core.presentation.wiring

import androidx.compose.runtime.ProvidedValue
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.safecall.FailureReporting
import io.thernal.archkit.core.presentation.api.store.StoreFactory
import io.thernal.archkit.core.presentation.api.viewmodel.resolver.LocalViewModelResolver
import io.thernal.archkit.core.presentation.impl.store.factory.StoreFactoryImpl
import io.thernal.archkit.core.presentation.api.store.observer.StoreObserver
import io.thernal.archkit.core.presentation.api.store.settings.StoreSettings
import io.thernal.archkit.core.presentation.impl.viewmodel.resolver.AppViewModelFactory
import io.thernal.archkit.core.presentation.impl.viewmodel.resolver.MetroViewModelResolver
import kotlin.reflect.KClass

private typealias ManualFactories =
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>

/**
 * The [StoreFactory] every ViewModel takes (`storeFactory: StoreFactory`), the app's
 * [MetroViewModelFactory], and the ViewModel resolver as a composition local. The app graph implements
 * Metro's `ViewModelGraph` (the ViewModel maps) and installs `ContributedLocals` and
 * `LocalMetroViewModelFactory` at the root.
 *
 * A failure a store's logic lets escape is reported to `FailureReporting` and the store keeps working;
 * what the user sees of it is the logic's business. Observers — a debug console, analytics — are
 * contributed into `Set<StoreObserver>`.
 */
@BindingContainer
@ContributesTo(AppScope::class)
interface PresentationProvidersModule {
    @Multibinds(allowEmpty = true)
    val observers: Set<StoreObserver>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideStoreSettings(observers: Set<StoreObserver>): StoreSettings {
            return StoreSettings(
                onError = { error ->
                    FailureReporting.reporter.report(
                        error = error,
                        isUnexpected = error !is Failure,
                    )
                },
                observers = observers.toList(),
            )
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideStoreFactory(settings: StoreSettings): StoreFactory {
            return StoreFactoryImpl(settings)
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
    }
}
