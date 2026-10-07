package io.thernal.archkit.core.presentation.api.plugin

import androidx.lifecycle.ViewModel

/** The name a handler logs its ViewModel under. */
internal fun ViewModel.ownerName(): String {
    return this::class.simpleName.orEmpty()
}
