package io.thernal.archkit.core.presentation.api.logic.slot

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.child.ChildScope
import io.thernal.archkit.core.presentation.api.logic.slice.Slice
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.forHandled
import io.thernal.archkit.core.presentation.api.store.state.Partial

/**
 * A child that exists only while it is open — a dialog, a bottom sheet, an inline editor. Decompose's
 * `childSlot`: whether it is open is the parent's state (`null` when closed), not a flag in the UI, so
 * the logic decides when it opens and closes, and a test sees it.
 *
 * [open] creates a new logic for its config, with a scope of its own; [close] cancels that scope, so a
 * request the child still has in flight dies with it and cannot write into the parent afterwards. The
 * child's effects are its outputs, as for a [Slice]: "sent" is usually the parent's cue to close it.
 *
 * Any [Logic] is a slot's child unchanged: a report screen's logic opens in a sheet as it is.
 */
class Slot<PS, PE, C, S : Any, I, E>(
    private val get: (PS) -> S?,
    private val set: PS.(S?) -> PS,
    private val create: (config: C) -> Hosted<S, I, E>,
    private val onEffect: StoreScope<PS, PE>.(E) -> Unit,
) {
    private var parent: StoreScope<PS, PE>? = null
    private var active: Pair<Logic<S, I, E>, ChildScope<PS, PE, S, E>>? = null

    val isOpen: Boolean get() = active != null

    fun attach(parent: StoreScope<PS, PE>) {
        this.parent = parent
    }

    /** Opens the child for [config], closing the one open now, if any. */
    fun open(config: C) {
        val parent = checkNotNull(parent) { "The slot is not attached: attach it in the parent's onStart" }
        close()
        val child = create(config)
        parent.forHandled().reduce(Partial { state -> state.set(child.initialState) })
        val scope = ChildScope(parent = parent, get = get, set = set, onEffect = onEffect)
        active = child.logic to scope
        with(child.logic) { scope.starting().onStart() }
    }

    /** Cancels the child's work and removes its state. Nothing happens when it is closed already. */
    fun close() {
        val (_, scope) = active ?: return
        active = null
        scope.close()
        parent?.forHandled()?.reduce(Partial { state -> state.set(null) })
    }

    /** Goes to the open child; dropped when none is open — an intent from a sheet that is closing. */
    fun postIntent(intent: I) {
        val (logic, scope) = active ?: return
        with(logic) { scope.receive(intent).onIntent(intent) }
    }
}
