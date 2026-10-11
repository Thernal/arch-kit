package io.thernal.archkit.core.presentation.api.viewmodel.factory

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner

/** The screen's ViewModel, from the graph. */
@Composable
inline fun <reified T : ViewModel> createViewModel(key: String? = null): T {
    return LocalViewModelResolver.current.resolve(modelClass = T::class, key = key, owner = screenOwner())
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
    return LocalViewModelResolver.current.resolve(
        modelClass = T::class,
        key = key,
        owner = rememberViewModelStoreOwner(),
    )
}

/** [componentViewModel] for a component whose ViewModel takes arguments (the post it shows). */
@Composable
inline fun <reified T : ViewModel, reified F : AssistedFactory> assistedComponentViewModel(
    key: String? = null,
    noinline create: F.(CreationExtras) -> T,
): T {
    return LocalViewModelResolver.current.resolveAssisted(
        modelClass = T::class,
        factoryClass = F::class,
        key = key,
        owner = rememberViewModelStoreOwner(),
        create = create,
    )
}

/** The owner a screen's ViewModels live in: the navigation entry's, from composition. */
@PublishedApi
@Composable
internal fun screenOwner(): ViewModelStoreOwner {
    return checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner in composition" }
}
