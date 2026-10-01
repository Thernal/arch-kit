package io.thernal.archkit.core.domain.failure

/**
 * The one exception a repository or use case lets out: what went wrong in terms the domain knows —
 * field errors for a form, a status from the backend, a message — never a transport or platform
 * exception. Anything else caught by `safeCall` is wrapped as `Failure(cause)` and reported as
 * unexpected.
 */
data class Failure(
    val errors: List<FieldError> = emptyList(),
    val status: ResponseStatus = ResponseStatus(),
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    companion object {
        /** Wraps an unexpected [throwable]: its message, kept as the cause. */
        fun of(throwable: Throwable): Failure {
            return throwable as? Failure ?: Failure(
                message = throwable.message ?: throwable.toString(),
                cause = throwable,
            )
        }
    }
}

/** A backend's complaint about one field of a request. */
data class FieldError(
    val field: String,
    val error: String,
    val code: Int? = null,
)

/** A backend's status for the whole request: an application code and its name. */
data class ResponseStatus(
    val code: Int = -1,
    val error: String? = null,
)
