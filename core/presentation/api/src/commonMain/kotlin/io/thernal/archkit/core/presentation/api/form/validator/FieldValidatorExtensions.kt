package io.thernal.archkit.core.presentation.api.form.validator

import io.thernal.archkit.core.presentation.api.form.state.validate

/** The first failing validator's message: field messages stay predictable. */
operator fun <V> FieldValidator<V>.plus(other: FieldValidator<V>): FieldValidator<V> {
    val validators = listOf(this, other)
    return FieldValidator { value -> validators.firstNotNullOfOrNull { it.validate(value) } }
}
