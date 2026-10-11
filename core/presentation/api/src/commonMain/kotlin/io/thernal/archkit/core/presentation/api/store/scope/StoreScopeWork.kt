package io.thernal.archkit.core.presentation.api.store.scope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Work that dies with the store, and counts as work: the store is busy while it runs. */
fun StoreScope<*, *>.launch(block: suspend CoroutineScope.() -> Unit): Job {
    return coroutineScope.launch {
        val self = this
        busy { self.block() }
    }
}

/** A coroutine that is not work: it can run for as long as the store lives, and the store is not busy for it. */
internal fun StoreScope<*, *>.launchBinder(block: suspend CoroutineScope.() -> Unit): Job {
    return coroutineScope.launch(block = block)
}
