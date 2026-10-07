package io.thernal.archkit.core.presentation.api.form

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.form.transformer.InputTransformer
import io.thernal.archkit.core.presentation.api.form.validator.FieldValidator

typealias TextFormField = FormField<TextFieldValue>

fun textFormField(
    value: String = "",
    validator: FieldValidator<TextFieldValue>? = null,
): TextFormField {
    return FormField(value = TextFieldValue(text = value, selection = TextRange(value.length)), validator = validator)
}

/** Applies [transformers] in order to an edit, then updates the field with the result. */
fun TextFormField.edit(
    new: TextFieldValue,
    vararg transformers: InputTransformer,
): TextFormField {
    return update(transformers.fold(new) { edited, transformer -> transformer.transform(old = value, new = edited) })
}
