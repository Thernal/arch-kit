package io.thernal.archkit.core.presentation.api.form.transformer

import androidx.compose.ui.text.input.TextFieldValue

/** Rewrites or rejects an edit before it reaches the field. */
fun interface InputTransformer {
    fun transform(
        old: TextFieldValue,
        new: TextFieldValue,
    ): TextFieldValue
}
