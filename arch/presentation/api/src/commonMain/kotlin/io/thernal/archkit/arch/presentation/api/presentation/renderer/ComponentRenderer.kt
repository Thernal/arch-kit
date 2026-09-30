package io.thernal.archkit.arch.presentation.api.presentation.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue

/**
 * A capability one feature exposes so another can render it without depending on its
 * implementation. The feature's `api` owns the contract, its `CompositionLocal` (whose default draws
 * nothing, so previews compose) and a composable facade; `impl` owns the real composables; `wiring`
 * contributes `LocalX provides XImpl()` into the graph's `Set<ProvidedValue<*>>`, which the root
 * installs with [ComponentLocals].
 */
interface ComponentRenderer

/** Installs every contributed composition local; the root wraps the app in it once. */
@Composable
fun ComponentLocals(
    values: Set<ProvidedValue<*>>,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(values = values.toTypedArray(), content = content)
}
