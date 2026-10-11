package io.thernal.archkit.core.presentation.api.composition.renderer

import io.thernal.archkit.core.presentation.api.composition.locals.ContributedLocals

/**
 * A capability one feature exposes so another can render it without depending on its
 * implementation. The feature's `api` owns the contract, its `CompositionLocal` (whose default draws
 * nothing, so previews compose) and a composable facade; `impl` owns the real composables; `wiring`
 * contributes `LocalX provides XImpl()` into the graph's `Set<ProvidedValue<*>>`, which the root
 * installs with [ContributedLocals].
 */
interface ComponentRenderer
