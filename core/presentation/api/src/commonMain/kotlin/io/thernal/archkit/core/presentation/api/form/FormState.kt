package io.thernal.archkit.core.presentation.api.form

/** A state that is a form: its fields, valid when every one is. */
interface FormState {
    val fields: List<FormField<*>>

    val isValid: Boolean get() = fields.all(FormField<*>::isValid)
}
