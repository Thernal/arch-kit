package io.thernal.archkit.core.presentation.api.slice

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.handle
import io.thernal.archkit.core.presentation.api.logic.start
import io.thernal.archkit.core.presentation.api.store.StoreScope

/**
 * Level 4. Runs [logic] on one part of a parent's state: it reads and reduces only that part. The logic
 * cannot tell it is a slice, so the same class is hosted as a screen, a component ViewModel, or a slice
 * of a bigger screen — moving it is a change of host, not of code.
 *
 * A slice's effects are its outputs, and they go to the parent, not to the screen: [onEffect] decides —
 * forward one to the screen (`emit`), or act on it (change the parent's state, send another slice an
 * intent). This is Decompose's rule: a child talks only to its parent, and the parent alone knows its
 * children.
 *
 * Slices never see each other. When one needs something another has, the parent passes it — as the
 * parameters of an intent it sends — and rules that span slices (nothing is editable while publishing)
 * are the parent's.
 *
 * Attach every slice in the parent's `onStart`. Each one gets a scope of its own inside the parent's —
 * Decompose's child lifecycle — so [detach] stops all of its work and nothing else.
 */
class Slice<PS, PE, S, I, E>(
    private val logic: Logic<S, I, E>,
    private val get: (PS) -> S,
    private val set: PS.(S) -> PS,
    private val onEffect: StoreScope<PS, PE>.(E) -> Unit,
) {
    private var scope: ChildScope<PS, PE, S, E>? = null

    fun attach(parent: StoreScope<PS, PE>) {
        check(scope == null) { "The slice is attached already" }
        val child = ChildScope(parent = parent, get = get, set = set, onEffect = onEffect)
        scope = child
        logic.start(child)
    }

    /** Cancels the slice's work. Its part of the state stays as it was. */
    fun detach() {
        scope?.close()
        scope = null
    }

    fun postIntent(intent: I) {
        val scope = checkNotNull(scope) { "The slice is not attached: attach it in the parent's onStart" }
        logic.handle(scope = scope, intent = intent)
    }
}
