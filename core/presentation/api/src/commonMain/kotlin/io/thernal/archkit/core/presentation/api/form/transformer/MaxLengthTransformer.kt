package io.thernal.archkit.core.presentation.api.form.transformer

import androidx.compose.ui.text.input.TextFieldValue

/** Rejects an edit that would make the text longer than [maxLength]. */
class MaxLengthTransformer(
    private val maxLength: Int,
) : InputTransformer {
    override fun transform(
        old: TextFieldValue,
        new: TextFieldValue,
    ): TextFieldValue {
        return if (new.text.length <= maxLength) {
            new
        } else {
            old
        }
    }
}
