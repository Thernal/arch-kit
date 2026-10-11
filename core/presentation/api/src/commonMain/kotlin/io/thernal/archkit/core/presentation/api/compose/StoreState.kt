package io.thernal.archkit.core.presentation.api.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.thernal.archkit.core.presentation.api.store.Store

/** The store's state for a screen, collected while the screen is at least started. */
@Composable
fun <S> Store<S, *, *>.collectAsState(): State<S> {
    return state.collectAsStateWithLifecycle()
}
