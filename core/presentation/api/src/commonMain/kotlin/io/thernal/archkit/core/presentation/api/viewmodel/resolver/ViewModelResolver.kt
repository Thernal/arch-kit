package io.thernal.archkit.core.presentation.api.viewmodel.resolver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import io.thernal.archkit.core.presentation.api.viewmodel.assisted.AssistedFactory
import kotlin.reflect.KClass

/**
 * Resolves DI-constructed ViewModels inside composition: the existing instance in [owner] for its key,
 * or a new one from the graph. `impl` owns the one call into the DI framework (Metro); features only
 * ever call [createViewModel], [componentViewModel] and their assisted forms, which pick the owner —
 * the screen's, or one scoped to a component.
 */
interface ViewModelResolver {
    @Composable
    fun <T : ViewModel> resolve(
        modelClass: KClass<T>,
        key: String?,
        owner: ViewModelStoreOwner,
    ): T

    @Composable
    fun <T : ViewModel, F : AssistedFactory> resolveAssisted(
        modelClass: KClass<T>,
        factoryClass: KClass<F>,
        key: String?,
        owner: ViewModelStoreOwner,
        create: F.(CreationExtras) -> T,
    ): T
}

/** No default: there is no safe stand-in for an arbitrary ViewModel. A preview passes its own instance. */
val LocalViewModelResolver = staticCompositionLocalOf<ViewModelResolver> {
    error(
        "No ViewModelResolver installed — add core/presentation/wiring to the app graph and " +
            "install ContributedLocals",
    )
}
