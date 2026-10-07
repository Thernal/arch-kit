package io.thernal.archkit.core.presentation.api.content

import io.thernal.archkit.core.domain.failure.Failure

/** The data of a success, or the placeholder of a loading state. */
fun <T> ContentState<T>.dataOrNull(): T? {
    return when (this) {
        is ContentState.Loading -> mock
        is ContentState.Success -> data
        ContentState.Idle, is ContentState.Error -> null
    }
}

val ContentState<*>.isIdle: Boolean get() = this is ContentState.Idle
val ContentState<*>.isLoading: Boolean get() = this is ContentState.Loading
val ContentState<*>.isSuccess: Boolean get() = this is ContentState.Success
val ContentState<*>.isError: Boolean get() = this is ContentState.Error

inline fun <T, R> ContentState<T>.map(transform: (T) -> R): ContentState<R> {
    return when (this) {
        ContentState.Idle -> ContentState.Idle
        is ContentState.Loading -> ContentState.Loading(mock?.let(transform))
        is ContentState.Success -> ContentState.Success(transform(data))
        is ContentState.Error -> this
    }
}

/** Loading, keeping what is already shown as the placeholder. */
fun <T> ContentState<T>.reload(): ContentState<T> {
    return ContentState.Loading(mock = dataOrNull())
}

/** A result as content: success, or the failure it carries. */
fun <T> Result<T>.toContentState(): ContentState<T> {
    return fold(
        onSuccess = { ContentState.Success(it) },
        onFailure = { ContentState.Error(Failure.of(it)) },
    )
}
