package io.thernal.archkit.arch.presentation.api.presentation.model

import androidx.compose.runtime.Immutable

/** What a screen shows; a feature's own state implements it. */
@Immutable
interface ViewState

/** What the user did; a feature's own intents implement it. */
@Immutable
interface ViewIntent

/** A one-off thing to do on screen — not state: shown once, never re-rendered. */
@Immutable
interface BaseEffect

/** A feature's own effects implement this; [MessageEffect] and [NavEffect] ship with the kit. */
@Immutable
interface ViewEffect : BaseEffect
