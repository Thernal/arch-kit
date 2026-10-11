package io.thernal.archkit.core.presentation.impl.viewmodel.resolver

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import io.thernal.archkit.core.presentation.api.viewmodel.assisted.AssistedFactory
import io.thernal.archkit.core.presentation.api.viewmodel.resolver.ViewModelResolver
import kotlin.reflect.KClass

/**
 * The only place the kit calls into Metro: `LocalMetroViewModelFactory` for a constructor-injected
 * ViewModel, the manual assisted factories for one built through an [AssistedFactory].
 */
object MetroViewModelResolver : ViewModelResolver {
    @Composable
    override fun <T : ViewModel> resolve(
        modelClass: KClass<T>,
        key: String?,
        owner: ViewModelStoreOwner,
    ): T {
        return viewModel(
            modelClass = modelClass,
            viewModelStoreOwner = owner,
            key = key,
            factory = LocalMetroViewModelFactory.current,
        )
    }

    @Composable
    override fun <T : ViewModel, F : AssistedFactory> resolveAssisted(
        modelClass: KClass<T>,
        factoryClass: KClass<F>,
        key: String?,
        owner: ViewModelStoreOwner,
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
        return viewModel(modelClass = modelClass, viewModelStoreOwner = owner, key = key, factory = factory)
    }
}
