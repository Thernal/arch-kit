package io.thernal.archkit.core.presentation.api.form.validator

import io.thernal.archkit.core.presentation.api.string.UiString

/** The message for an invalid value, or null for a valid one. */
fun interface FieldValidator<V> {
    fun validate(value: V): UiString?
}
