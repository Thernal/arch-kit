package io.thernal.archkit.core.presentation.api.store.scope

/** This scope for the chain the store handles now; itself when it handles none or is not traced. */
internal fun <S, E> StoreScope<S, E>.forHandled(): StoreScope<S, E> {
    val tracing = this as? TracedScope<S, E> ?: return this
    return tracing.handling?.let(tracing::traced) ?: this
}
