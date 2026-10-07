package io.thernal.archkit.core.presentation.api.content

import androidx.compose.runtime.Immutable
import io.thernal.archkit.core.domain.failure.Failure

/**
 * Remote data on screen: not asked for yet, loading (with placeholder data to shimmer over), there,
 * or failed.
 */
@Immutable
sealed interface ContentState<out T> {
    data object Idle : ContentState<Nothing>

    data class Loading<T>(
        val mock: T? = null,
    ) : ContentState<T>

    data class Success<T>(
        val data: T,
    ) : ContentState<T>

    data class Error(
        val failure: Failure,
    ) : ContentState<Nothing>
}
