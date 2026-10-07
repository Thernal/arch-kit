package io.thernal.archkit.core.presentation.api.form

import androidx.compose.runtime.Immutable
import io.thernal.archkit.core.presentation.api.form.validator.FieldValidator
import io.thernal.archkit.core.presentation.api.string.UiString

/**
 * A form field: its value, the error shown under it, and the validator that produces that error.
 * [update] clears the error — the user is fixing it; [validate] runs the validator; [withError] sets
 * one from elsewhere (a form rule, the server).
 */
@Immutable
data class FormField<V>(
    val value: V,
    val error: UiString? = null,
    val validator: FieldValidator<V>? = null,
) {
    val isValid: Boolean get() = error == null && validator?.validate(value) == null

    fun update(value: V): FormField<V> {
        return copy(value = value, error = null)
    }

    fun validate(): FormField<V> {
        return copy(error = validator?.validate(value))
    }

    fun withError(error: UiString?): FormField<V> {
        return copy(error = error)
    }
}
