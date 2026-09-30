package io.thernal.archkit.arch.presentation.impl.presentation.factory

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import io.thernal.archkit.arch.presentation.api.presentation.factory.AssistedFactory
import io.thernal.archkit.arch.presentation.api.presentation.factory.ViewModelFactoryRenderer
import kotlin.reflect.KClass

private typealias ManualFactories =
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>

/** The one [MetroViewModelFactory] an app installs through `LocalMetroViewModelFactory`. */
class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: ManualFactories,
) : MetroViewModelFactory()

/**
 * The only place the kit calls into Metro: `LocalMetroViewModelFactory` for a constructor-injected
 * ViewModel, the manual assisted factories for one built through an [AssistedFactory].
 */
object MetroViewModelFactoryRenderer : ViewModelFactoryRenderer {
    @Composable
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        key: String?,
    ): T {
        return viewModel(modelClass = modelClass, key = key, factory = LocalMetroViewModelFactory.current)
    }

    @Composable
    override fun <T : ViewModel, F : AssistedFactory> createAssisted(
        modelClass: KClass<T>,
        factoryClass: KClass<F>,
        key: String?,
        create: F.(CreationExtras) -> T,
    ): T {
        val metro = LocalMetroViewModelFactory.current
        val factory = object : ViewModelProvider.Factory {
            // `modelClass` is the overridden interface's own parameter name; the outer one is the same class.
            @Suppress("UNCHECKED_CAST", "NoNameShadowing") // A feature's assisted factory implements both markers.
            override fun <VM : ViewModel> create(
                modelClass: KClass<VM>,
                extras: CreationExtras,
            ): VM {
                val manualClass = factoryClass as KClass<ManualViewModelAssistedFactory>
                val manual = metro.createManuallyAssistedFactory(manualClass)()
                return (manual as F).create(extras) as VM
            }
        }
        return viewModel(modelClass = modelClass, key = key, factory = factory)
    }
}
