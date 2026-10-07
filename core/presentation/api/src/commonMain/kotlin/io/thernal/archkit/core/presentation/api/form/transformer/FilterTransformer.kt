package io.thernal.archkit.core.presentation.api.form.transformer

import androidx.compose.ui.text.input.TextFieldValue

/** Keeps only the characters [allowed] accepts: digits for a code, a phone number. */
class FilterTransformer(
    private val allowed: (Char) -> Boolean,
) : InputTransformer {
    override fun transform(
        old: TextFieldValue,
        new: TextFieldValue,
    ): TextFieldValue {
        return if (new.text.all(allowed)) {
            new
        } else {
            old
        }
    }
}
