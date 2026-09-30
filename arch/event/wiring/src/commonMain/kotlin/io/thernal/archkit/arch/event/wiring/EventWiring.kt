package io.thernal.archkit.arch.event.wiring

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.thernal.archkit.arch.event.api.domain.EventBus
import io.thernal.archkit.arch.event.impl.domain.SharedFlowEventBus

/** The one [EventBus] of the app. */
@BindingContainer
@ContributesTo(AppScope::class)
interface EventWiring {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideEventBus(): EventBus {
            return SharedFlowEventBus()
        }
    }
}
