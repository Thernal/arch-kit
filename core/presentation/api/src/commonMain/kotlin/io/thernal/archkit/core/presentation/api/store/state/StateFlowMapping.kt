package io.thernal.archkit.core.presentation.api.store.state

import io.thernal.archkit.core.presentation.api.content.map
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [source] mapped with [transform], still a [StateFlow]: no scope, nothing kept, computed on read. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class MappedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R,
) : StateFlow<R> {
    override val value: R get() = transform(source.value)
    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        coroutineScope { source.map(transform).distinctUntilChanged().collect(collector) }
        awaitCancellation()
    }
}

/** This state flow mapped with [transform]: still a [StateFlow], computed on read, nothing kept. */
fun <T, R> StateFlow<T>.mapped(transform: (T) -> R): StateFlow<R> {
    return MappedStateFlow(source = this, transform = transform)
}
