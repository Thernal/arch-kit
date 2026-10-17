package io.thernal.archkit.core.presentation.api.compose.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * One component's state, mapped from the screen's: the component recomposes only when its own part
 * changes, as long as it draws nothing that does not depend on that part. ArenaGo's mapper style — the screen's state stays the logic's, every component gets the shape
 * it draws, and a component that later gets its own ViewModel already has its own state.
 */
@Composable
inline fun <T, R> State<T>.select(crossinline transform: (T) -> R): State<R> {
    return remember(this) { derivedStateOf { transform(value) } }
}

/** [select] whose mapping also reads [key] — it is rebuilt when the key changes. */
@Composable
inline fun <T, R> State<T>.select(
    key: Any?,
    crossinline transform: (T) -> R,
): State<R> {
    return remember(key1 = this, key2 = key) { derivedStateOf { transform(value) } }
}
