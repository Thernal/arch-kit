package io.thernal.archkit.core.event.impl.domain

import io.thernal.archkit.core.event.api.domain.BaseEvent
import io.thernal.archkit.core.event.api.domain.observe
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

private sealed interface SessionEvent : BaseEvent

private data object SignedOut : SessionEvent

private data class Expired(
    val reason: String,
) : SessionEvent

private data object CartChanged : BaseEvent

class SharedFlowEventBusTest {
    @Test
    fun anObserverSeesItsTypeAndItsSubtypesOnly() {
        runTest {
            val bus = SharedFlowEventBus()
            val session = mutableListOf<SessionEvent>()
            val expired = mutableListOf<Expired>()
            val a = launch { bus.observe<SessionEvent>().take(2).toList(session) }
            val b = launch { bus.observe<Expired>().take(1).toList(expired) }
            yield()

            bus.fire(CartChanged)
            bus.fire(SignedOut)
            bus.fire(Expired(reason = "401"))
            a.join()
            b.join()

            assertEquals(expected = listOf(SignedOut, Expired("401")), actual = session)
            assertEquals(expected = listOf(Expired("401")), actual = expired)
        }
    }
}
