package io.thernal.archkit.core.presentation.api.viewmodel.factory

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectPlugin
import io.thernal.archkit.core.presentation.api.plugin.effect.OnEffectUpdate
import io.thernal.archkit.core.presentation.api.plugin.effect.ViewEffect
import kotlinx.coroutines.CoroutineScope

/** The screen's ViewModel, from the graph; its messages and navigation are handled already. */
@Composable
inline fun <reified T : ViewModel> createViewModel(key: String? = null): T {
    val viewModel = LocalViewModelResolver.current.resolve(modelClass = T::class, key = key, owner = screenOwner())
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
    val viewModel = LocalViewModelResolver.current.resolve(modelClass = T::class, key = key, owner = screenOwner())
    viewModel.OnEffectUpdate(collector = collector)
    return viewModel
}

/** A ViewModel with per-call arguments DI cannot inject (a route parameter), through its [F] factory. */
@Composable
inline fun <reified T : ViewModel, reified F : AssistedFactory> createAssistedViewModel(
    key: String? = null,
    noinline create: F.(CreationExtras) -> T,
): T {
    return LocalViewModelResolver.current.resolveAssisted(
        modelClass = T::class,
        factoryClass = F::class,
        key = key,
        owner = screenOwner(),
        create = create,
    )
}

/**
 * A ViewModel for one self-contained component rather than a screen (a post card with its own like
 * and share). It lives as long as the component is in composition — created when it enters, cleared
 * when it leaves, kept across a rotation — not as long as the screen: an item scrolled out of a lazy
 * list gets a fresh one when it comes back.
 */
@Composable
inline fun <reified T : ViewModel> componentViewModel(key: String? = null): T {
    val viewModel = LocalViewModelResolver.current.resolve(
        modelClass = T::class,
        key = key,
        owner = rememberViewModelStoreOwner(),
    )
    if (viewModel is EffectPlugin<*>) {
        viewModel.OnEffectUpdate()
    }
    return viewModel
}

/** [componentViewModel] for a component whose ViewModel takes arguments (the post it shows). */
@Composable
inline fun <reified T : ViewModel, reified F : AssistedFactory> assistedComponentViewModel(
    key: String? = null,
    noinline create: F.(CreationExtras) -> T,
): T {
    val viewModel = LocalViewModelResolver.current.resolveAssisted(
        modelClass = T::class,
        factoryClass = F::class,
        key = key,
        owner = rememberViewModelStoreOwner(),
        create = create,
    )
    if (viewModel is EffectPlugin<*>) {
        viewModel.OnEffectUpdate()
    }
    return viewModel
}

/** The owner a screen's ViewModels live in: the navigation entry's, from composition. */
@PublishedApi
@Composable
internal fun screenOwner(): ViewModelStoreOwner {
    return checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner in composition" }
}
