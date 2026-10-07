package io.thernal.archkit.core.presentation.api.plugin.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.thernal.archkit.core.presentation.api.plugin.PluginContext
import io.thernal.archkit.core.presentation.api.plugin.factory
import io.thernal.archkit.core.presentation.api.plugin.ownerName

/** The ViewModel's state holder. Compiles only in a ViewModel that is a [PluginContext]. */
context(context: PluginContext, viewModel: ViewModel)
fun <S : ViewState> StateHandler(
    log: Boolean = true,
    initializer: () -> S,
): StateHandler<S> {
    return context.plugins.factory<StateFactory>().create(
        owner = viewModel.ownerName(),
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
