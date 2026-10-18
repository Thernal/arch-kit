package io.thernal.archkit.core.presentation.api.logic.child

import io.thernal.archkit.core.presentation.api.store.state.Partial

/**
 * A child's [partial] applied to its part of the parent's state. A slice or a slot changes the parent's state
 * through one, so the parent store's observers can name the child's change instead of a wrapper.
 */
class ChildPartial<PS, S> internal constructor(
    val partial: Partial<S>,
    private val get: (PS) -> S?,
    private val set: PS.(S) -> PS,
) : Partial<PS> {
    override fun reduce(state: PS): PS {
        val part = get(state) ?: return state
        return state.set(partial.reduce(part))
    }
}
