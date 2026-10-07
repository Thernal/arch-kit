package io.thernal.archkit.core.presentation.api.plugin.effect

import androidx.compose.runtime.Immutable

/** A feature's own effects implement this; `MessageEffect` and `NavEffect` ship with the kit. */
@Immutable
interface ViewEffect : BaseEffect
