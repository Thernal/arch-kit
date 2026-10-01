package io.thernal.archkit.core.presentation.api.presentation.form.transformer

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.presentation.form.InputTransformer

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

/** Drops leading whitespace as it is typed. */
data object TrimStartTransformer : InputTransformer {
    override fun transform(
        old: TextFieldValue,
        new: TextFieldValue,
    ): TextFieldValue {
        return new.copy(text = new.text.trimStart())
    }
}

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
