package io.thernal.archkit.arch.presentation.api.presentation.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.thernal.archkit.arch.presentation.api.presentation.model.ViewState
import io.thernal.archkit.arch.presentation.api.presentation.plugin.PluginContext
import io.thernal.archkit.arch.presentation.api.presentation.plugin.PluginFactory
import io.thernal.archkit.arch.presentation.api.presentation.plugin.factory
import kotlinx.coroutines.flow.StateFlow

interface StateHandler<S : ViewState> {
    val state: StateFlow<S>
    val currentState: S

    fun setState(reducer: S.() -> S)
}

interface StateFactory : PluginFactory {
    fun <S : ViewState> create(
        owner: String,
        log: Boolean,
        initializer: () -> S,
    ): StateHandler<S>
}

interface StatePlugin<S : ViewState> : PluginContext {
    val stateHandler: StateHandler<S>
}

/** The ViewModel's state holder. Compiles only in a ViewModel that is a [PluginContext]. */
context(context: PluginContext, viewModel: ViewModel)
fun <S : ViewState> StateHandler(
    log: Boolean = true,
    initializer: () -> S,
): StateHandler<S> {
    return context.plugins.factory<StateFactory>().create(
        owner = viewModel.name(),
        log = log,
        initializer = initializer,
    )
}

/** Replaces the state with [reducer]'s copy of it. Compiles only inside a ViewModel. */
context(_: ViewModel, plugin: StatePlugin<S>)
fun <S : ViewState> setState(reducer: S.() -> S) {
    plugin.stateHandler.setState(reducer)
}

/** The current state, for reading inside the ViewModel. */
val <S : ViewState> StatePlugin<S>.state: S get() = stateHandler.currentState

/** The state for a screen, collected while the screen is at least started. */
@Composable
fun <S : ViewState> StatePlugin<S>.collectAsState(): State<S> {
    return stateHandler.state.collectAsStateWithLifecycle()
}

/** One part of the state; the screen recomposes only when that part changes. */
@Composable
inline fun <S : ViewState, R> StatePlugin<S>.collectAsState(crossinline select: (S) -> R): State<R> {
    val state = collectAsState()
    return remember(state) { derivedStateOf { select(state.value) } }
}

internal fun ViewModel.name(): String {
    return this::class.simpleName.orEmpty()
}
