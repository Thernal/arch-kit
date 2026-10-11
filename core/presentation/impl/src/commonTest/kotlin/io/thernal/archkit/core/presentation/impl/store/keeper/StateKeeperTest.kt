package io.thernal.archkit.core.presentation.impl.store.keeper

import androidx.lifecycle.SavedStateHandle
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.keeper.DEFAULT_SAVE_INTERVAL_MILLIS
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.setState
import io.thernal.archkit.core.presentation.impl.store.host.StoreHost
import io.thernal.archkit.core.testing.testStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StateKeeperTest {
    private data class Form(
        val name: String = "",
        val isSaving: Boolean = false,
    )

    private class Typing : Logic<Form, String, Nothing> {
        override fun StoreScope<Form, Nothing>.onIntent(intent: String) {
            setState { copy(name = intent, isSaving = true) }
        }
    }

    private fun SavedStateHandle.formKeeper() = testStoreFactory().keeper<Form, String>(
        handle = this,
        key = "form",
        serializer = String.serializer(),
        save = { form -> form.name },
        restore = { saved -> copy(name = saved) },
    )

    @Test
    fun theKeptPartSurvivesANewStoreAndTheRestStartsFresh() = runTest {
        val handle = SavedStateHandle()
        val before = StoreHost(Form(), backgroundScope, Typing(), keeper = handle.formKeeper())
        before.postIntent("Ada")
        runCurrent()

        // The process dies: only the handle's contents survive, into a new store.
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { key -> handle.get<Any>(key) })
        val after = StoreHost(Form(), backgroundScope, Typing(), keeper = restoredHandle.formKeeper())
        assertEquals(Form(name = "Ada"), after.state.value)
    }

    @Test
    fun aSavedValueThatNoLongerDecodesIsDropped() = runTest {
        val handle = SavedStateHandle(mapOf("form" to "{\"title\": 42}"))
        val store = StoreHost(Form(), backgroundScope, Typing(), keeper = handle.formKeeper())
        assertEquals(Form(), store.state.value)
    }

    @Test
    fun typingIsWrittenOnceAnIntervalAndTheLastValueIsNotLost() = runTest {
        val handle = SavedStateHandle()
        val store = StoreHost(Form(), backgroundScope, Typing(), keeper = handle.formKeeper())
        listOf("A", "Ad", "Ada").forEach { typed ->
            store.postIntent(typed)
            runCurrent()
        }
        // The first change is written at once; the others wait for the interval, and then the last is written.
        assertEquals("\"A\"", handle.get<String>("form"))
        advanceTimeBy(DEFAULT_SAVE_INTERVAL_MILLIS + 1)
        assertEquals("\"Ada\"", handle.get<String>("form"))
    }
}
