package io.thernal.archkit.arch.presentation.api.presentation.form

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.arch.presentation.api.presentation.string.UiString

/** The message for an invalid value, or null for a valid one. */
fun interface FieldValidator<V> {
    fun validate(value: V): UiString?
}

/** The first failing validator's message: field messages stay predictable. */
operator fun <V> FieldValidator<V>.plus(other: FieldValidator<V>): FieldValidator<V> {
    val validators = listOf(this, other)
    return FieldValidator { value -> validators.firstNotNullOfOrNull { it.validate(value) } }
}

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

typealias TextFormField = FormField<TextFieldValue>

fun textFormField(
    value: String = "",
    validator: FieldValidator<TextFieldValue>? = null,
): TextFormField {
    return FormField(value = TextFieldValue(text = value, selection = TextRange(value.length)), validator = validator)
}

/** Rewrites or rejects an edit before it reaches the field. */
fun interface InputTransformer {
    fun transform(
        old: TextFieldValue,
        new: TextFieldValue,
    ): TextFieldValue
}

/** Applies [transformers] in order to an edit, then updates the field with the result. */
fun TextFormField.edit(
    new: TextFieldValue,
    vararg transformers: InputTransformer,
): TextFormField {
    return update(transformers.fold(new) { edited, transformer -> transformer.transform(old = value, new = edited) })
}
