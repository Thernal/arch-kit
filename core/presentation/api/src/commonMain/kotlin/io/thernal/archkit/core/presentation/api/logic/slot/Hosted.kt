package io.thernal.archkit.core.presentation.api.logic.slot

import io.thernal.archkit.core.presentation.api.logic.Logic

/** What a slot hosts: the state it starts with and its logic. */
class Hosted<S, I, E>(
    val initialState: S,
    val logic: Logic<S, I, E>,
)
