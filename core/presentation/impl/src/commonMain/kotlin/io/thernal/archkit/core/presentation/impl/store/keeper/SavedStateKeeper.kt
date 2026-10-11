package io.thernal.archkit.core.presentation.impl.store.keeper

import androidx.lifecycle.SavedStateHandle
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A [StateKeeper] that writes a JSON of the kept part into a [SavedStateHandle]. */
class SavedStateKeeper<S, T>(
    private val handle: SavedStateHandle,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val save: (S) -> T,
    private val restore: S.(T) -> S,
    private val saveIntervalMillis: Long,
) : StateKeeper<S> {
    /** [initial] with what was saved before the process died, merged in. For a host, once, at creation. */
    fun restore(initial: S): S {
        val saved = handle.get<String>(key)
        // A saved value that no longer decodes — the app was updated meanwhile — is dropped.
        val part = saved?.let { json ->
            try {
                keeperJson.decodeFromString(deserializer = serializer, string = json)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return if (part == null) {
            initial
        } else {
            initial.restore(part)
        }
    }

    /** Saves the part of [states] the keeper keeps, until cancelled. For a host, in the store's scope. */
    suspend fun save(states: Flow<S>) {
        // The first change is written at once; the ones that follow within the interval are merged into
        // one write of the last value, so typing is not encoded on every key.
        states.map(save).distinctUntilChanged().conflate().collect { part ->
            handle[key] = keeperJson.encodeToString(serializer = serializer, value = part)
            delay(saveIntervalMillis)
        }
    }
}

private val keeperJson = Json { ignoreUnknownKeys = true }
