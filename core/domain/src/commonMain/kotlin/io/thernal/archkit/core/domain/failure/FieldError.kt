package io.thernal.archkit.core.domain.failure

/** A backend's complaint about one field of a request. */
data class FieldError(
    val field: String,
    val error: String,
    val code: Int? = null,
)
