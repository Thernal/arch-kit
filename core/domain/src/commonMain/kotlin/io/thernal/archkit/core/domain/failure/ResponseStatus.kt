package io.thernal.archkit.core.domain.failure

/** A backend's status for the whole request: an application code and its name. */
data class ResponseStatus(
    val code: Int = -1,
    val error: String? = null,
)
