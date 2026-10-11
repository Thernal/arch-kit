package io.thernal.archkit.core.presentation.impl.logic.slot

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.logic.slice.slice
import io.thernal.archkit.core.presentation.api.logic.slot.Hosted
import io.thernal.archkit.core.presentation.api.logic.slot.Slot
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.scope.setState
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import io.thernal.archkit.core.presentation.impl.store.host.StoreHost
import io.thernal.archkit.core.presentation.api.store.settings.Start
import io.thernal.archkit.core.presentation.api.store.settings.StoreSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SlotTest {
    /** A rename dialog: saving takes a second, then says it is done. */
    private class Rename(
        private val saved: MutableList<String>,
    ) : Logic<String, String, String> {
        override fun StoreScope<String, String>.onIntent(intent: String) {
            if (intent != SAVE) {
                setState { intent }
                return
            }
            val name = state
            launch {
                delay(1_000)
                saved += name
                setState { "$name ✓" }
                emit(name)
            }
        }
    }

    private data class Screen(
        val title: String = "untitled",
        val rename: String? = null,
    )

    private sealed interface Action {
        data object Open : Action

        data class Dialog(
            val intent: String,
        ) : Action

        data object Dismiss : Action
    }

    private class Editor(
        saved: MutableList<String>,
    ) : Logic<Screen, Action, String> {
        private val rename: Slot<Screen, String, String, String, String, String> = Slot(
            get = Screen::rename,
            set = { copy(rename = it) },
            create = { title -> Hosted(title, Rename(saved)) },
            // The dialog's output: the parent takes the new title, closes the dialog, and tells the screen.
            onEffect = { name ->
                setState { copy(title = name) }
                rename.close()
                emit("renamed to $name")
            },
        )

        override fun StoreScope<Screen, String>.onStart() = rename.attach(this)

        override fun StoreScope<Screen, String>.onIntent(intent: Action) {
            when (intent) {
                Action.Open -> rename.open(state.title)
                is Action.Dialog -> rename.postIntent(intent.intent)
                Action.Dismiss -> rename.close()
            }
        }
    }

    @Test
    fun theChildLivesWhileOpenAndItsOutputGoesToTheParent() = runTest {
        val saved = mutableListOf<String>()
        val store = StoreHost(Screen(), backgroundScope, Editor(saved))
        val effects = mutableListOf<String>()
        backgroundScope.launch { store.effects.toList(effects) }
        runCurrent()

        store.postIntent(Action.Open)
        assertEquals("untitled", store.state.value.rename)
        store.postIntent(Action.Dialog("draft"))
        store.postIntent(Action.Dialog(SAVE))
        runCurrent()
        advanceTimeBy(2_000)

        assertEquals(Screen(title = "draft"), store.state.value)
        assertEquals(listOf("draft"), saved)
        assertEquals(listOf("renamed to draft"), effects)
    }

    @Test
    fun closingCancelsTheChildsWorkAndDropsItsIntents() = runTest {
        val saved = mutableListOf<String>()
        val store = StoreHost(Screen(), backgroundScope, Editor(saved))
        store.postIntent(Action.Open)
        store.postIntent(Action.Dialog(SAVE))
        runCurrent()
        advanceTimeBy(500)
        store.postIntent(Action.Dismiss)
        store.postIntent(Action.Dialog("ignored"))
        runCurrent()
        advanceTimeBy(2_000)

        assertNull(store.state.value.rename)
        assertEquals(emptyList(), saved)
        assertEquals("untitled", store.state.value.title)
    }

    @Test
    fun aDetachedSliceStopsItsWorkAndOnlyItsWork() = runTest {
        val left = slice(Counting(), get = { pair: Pair<Int, Int> -> pair.first }, set = { copy(first = it) })
        val right = slice(Counting(), get = { pair: Pair<Int, Int> -> pair.second }, set = { copy(second = it) })
        val parent = object : Logic<Pair<Int, Int>, Boolean, Nothing> {
            override fun StoreScope<Pair<Int, Int>, Nothing>.onStart() {
                left.attach(this)
                right.attach(this)
            }

            override fun StoreScope<Pair<Int, Int>, Nothing>.onIntent(intent: Boolean) {
                if (intent) {
                    left.detach()
                } else {
                    right.postIntent(Unit)
                }
            }
        }
        val store = StoreHost(0 to 0, backgroundScope, parent, StoreSettings(start = Start.Eagerly))
        runCurrent()
        advanceTimeBy(2_500)
        store.postIntent(true)
        runCurrent()
        advanceTimeBy(5_000)
        store.postIntent(false)

        // The left one stopped at 2; the right one kept ticking (7) and still takes intents (+100).
        assertEquals(2 to 107, store.state.value)
    }

    /** Ticks on its own, and counts the intents it gets. */
    private class Counting : Logic<Int, Unit, Nothing> {
        override fun StoreScope<Int, Nothing>.onStart() {
            launch {
                while (true) {
                    delay(1_000)
                    setState { this + 1 }
                }
            }
        }

        override fun StoreScope<Int, Nothing>.onIntent(intent: Unit) {
            setState { this + 100 }
        }
    }

    private companion object {
        const val SAVE = "save"
    }
}
