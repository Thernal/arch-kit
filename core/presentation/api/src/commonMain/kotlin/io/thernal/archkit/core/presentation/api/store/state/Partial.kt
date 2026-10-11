package io.thernal.archkit.core.presentation.api.store.state

/**
 * One change to the state. The store applies partials one at a time, each to the state as it is at that
 * moment, so two results arriving together never overwrite each other.
 *
 * Name it — a class in the screen's contract, its `reduce` beside it — when it is worth seeing in a log,
 * testing on its own, or producing from more than one place. Otherwise `setState { copy(…) }` is an
 * anonymous one.
 */
fun interface Partial<S> {
    fun reduce(state: S): S
}
