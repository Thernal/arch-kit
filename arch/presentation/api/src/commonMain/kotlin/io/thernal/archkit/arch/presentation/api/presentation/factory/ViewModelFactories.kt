package io.thernal.archkit.arch.presentation.api.presentation.factory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import io.thernal.archkit.arch.presentation.api.presentation.effect.EffectPlugin
import io.thernal.archkit.arch.presentation.api.presentation.effect.OnEffectUpdate
import io.thernal.archkit.arch.presentation.api.presentation.model.ViewEffect
import io.thernal.archkit.arch.presentation.api.presentation.renderer.ComponentRenderer
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/** Marker for a feature's manually invoked assisted ViewModel factory, for [createAssistedViewModel]. */
interface AssistedFactory

/**
 * Builds DI-constructed ViewModels. `impl` owns the one call into the DI framework (Metro); features
 * only ever call [createViewModel], [componentViewModel] and [createAssistedViewModel].
 */
interface ViewModelFactoryRenderer : ComponentRenderer {
    @Composable
    fun <T : ViewModel> create(
        modelClass: KClass<T>,
        key: String?,
    ): T

    @Composable
    fun <T : ViewModel, F : AssistedFactory> createAssisted(
        modelClass: KClass<T>,
        factoryClass: KClass<F>,
        key: String?,
        create: F.(CreationExtras) -> T,
    ): T
}

/** No default: there is no safe stand-in for an arbitrary ViewModel. A preview passes its own instance. */
val LocalViewModelFactoryRenderer = staticCompositionLocalOf<ViewModelFactoryRenderer> {
    error(
        "No ViewModelFactoryRenderer installed — add arch/presentation/wiring to the app graph and " +
            "install ComponentLocals",
    )
}

/** The screen's ViewModel, from the graph; its messages and navigation are handled already. */
@Composable
inline fun <reified T : ViewModel> createViewModel(key: String? = null): T {
    val viewModel = LocalViewModelFactoryRenderer.current.create(modelClass = T::class, key = key)
    if (viewModel is EffectPlugin<*>) {
        viewModel.OnEffectUpdate()
    }
    return viewModel
}

/** [createViewModel], also handing the feature's own effects to [collector]. */
@Composable
inline fun <reified T, reified E : ViewEffect> createViewModel(
    key: String? = null,
    noinline collector: suspend CoroutineScope.(E) -> Unit,
): T where T : ViewModel, T : EffectPlugin<E> {
    val viewModel = LocalViewModelFactoryRenderer.current.create(modelClass = T::class, key = key)
    viewModel.OnEffectUpdate(collector = collector)
    return viewModel
}

/** A ViewModel for one component rather than a screen — keyed, so each card has its own. */
@Composable
inline fun <reified T : ViewModel> componentViewModel(key: String? = null): T {
    return createViewModel(key = key)
}

/** A ViewModel with per-call arguments DI cannot inject (a route parameter), through its [F] factory. */
@Composable
inline fun <reified T : ViewModel, reified F : AssistedFactory> createAssistedViewModel(
    key: String? = null,
    noinline create: F.(CreationExtras) -> T,
): T {
    return LocalViewModelFactoryRenderer.current.createAssisted(
        modelClass = T::class,
        factoryClass = F::class,
        key = key,
        create = create,
    )
}
