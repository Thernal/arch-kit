package io.thernal.archkit.core.presentation.api.keeper

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun <S, T> SavedStateHandle.keeper(
    key: String,
    serializer: KSerializer<T>,
    save: (S) -> T,
    restore: S.(T) -> S,
    saveIntervalMillis: Long = DEFAULT_SAVE_INTERVAL_MILLIS,
): StateKeeper<S> {
    val handle = this
    return StateKeeper(
        onRestore = { initial ->
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
            if (part == null) {
                initial
            } else {
                initial.restore(part)
            }
        },
        onSave = { states ->
            // The first change is written at once; the ones that follow within the interval are merged into
            // one write of the last value, so typing is not encoded on every key.
            states.map(save).distinctUntilChanged().conflate().collect { part ->
                handle[key] = keeperJson.encodeToString(serializer = serializer, value = part)
                delay(saveIntervalMillis)
            }
        },
    )
}

private val keeperJson = Json { ignoreUnknownKeys = true }

const val DEFAULT_SAVE_INTERVAL_MILLIS = 300L
