package io.thernal.archkit.core.presentation.api.form.transformer

import androidx.compose.ui.text.input.TextFieldValue

/** Drops leading whitespace as it is typed. */
data object TrimStartTransformer : InputTransformer {
    override fun transform(
        old: TextFieldValue,
        new: TextFieldValue,
    ): TextFieldValue {
        return new.copy(text = new.text.trimStart())
    }
}
