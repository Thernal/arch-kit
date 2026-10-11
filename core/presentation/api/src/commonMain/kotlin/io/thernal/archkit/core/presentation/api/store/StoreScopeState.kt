package io.thernal.archkit.core.presentation.api.store

/** An anonymous [Partial]: `setState { copy(name = value) }`. */
fun <S, E> StoreScope<S, E>.setState(block: S.() -> S) {
    reduce(Partial { state -> state.block() })
}
