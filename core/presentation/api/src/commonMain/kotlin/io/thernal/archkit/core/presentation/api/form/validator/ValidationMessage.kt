package io.thernal.archkit.core.presentation.api.form.validator

import io.thernal.archkit.core.presentation.api.string.UiString

/** A validator's default message: a key the app can translate (`StringLookup`), with an English fallback. */
internal fun validationMessage(
    key: String,
    fallback: String,
): UiString {
    return UiString.Localized(key = key, fallback = UiString.Dynamic(fallback))
}
