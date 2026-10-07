package io.thernal.archkit.core.presentation.api.form

import io.thernal.archkit.core.presentation.api.plugin.state.ViewState

/** A state that is a form: its fields, valid when every one is. */
interface FormState : ViewState {
    val fields: List<FormField<*>>

    val isValid: Boolean get() = fields.all(FormField<*>::isValid)
}
