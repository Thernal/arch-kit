package io.thernal.archkit.core.presentation.api.slice

import io.thernal.archkit.core.presentation.api.logic.Logic

/** A slice whose effects go to the screen as they are — or that has none (`Nothing`). */
fun <PS, E, S, I> slice(
    logic: Logic<S, I, E>,
    get: (PS) -> S,
    set: PS.(S) -> PS,
): Slice<PS, E, S, I, E> {
    return Slice(logic = logic, get = get, set = set, onEffect = { effect -> emit(effect) })
}
