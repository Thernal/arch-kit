package io.thernal.archkit.core.presentation.api.composition.locals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue

/**
 * Installs every composition local the graph contributes — component renderers, the ViewModel
 * resolver, anything else a module provides as `LocalX provides …`. The root wraps the app in it once.
 */
@Composable
fun ContributedLocals(
    values: Set<ProvidedValue<*>>,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(values = values.toTypedArray(), content = content)
}
