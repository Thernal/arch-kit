package io.thernal.archkit.core.presentation.api.logic

import io.thernal.archkit.core.presentation.api.store.StoreScope

/** Runs [logic]'s `onStart` in [scope]. For a host: a store, a slice, a slot. */
fun <S, I, E> Logic<S, I, E>.start(scope: StoreScope<S, E>) {
    with(this) { scope.onStart() }
}

/** Runs [logic]'s `onIntent` for [intent] in [scope]. For a host: a store, a slice, a slot. */
fun <S, I, E> Logic<S, I, E>.handle(
    scope: StoreScope<S, E>,
    intent: I,
) {
    with(this) { scope.onIntent(intent) }
}
