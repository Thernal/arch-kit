# core/event/api

Events between features that do not know each other — "session expired", "cart changed". Package
`io.thernal.archkit.core.event.api.domain`; the bus is `SharedFlowEventBus` (`core/event/impl`), bound
once per app by `EventProvidersModule` (`core/event/wiring`).

## Define an event

```kotlin
data object SessionExpired : BaseEvent
data class CartChanged(val count: Int) : BaseEvent
```

An event is a fact that happened, named in the past tense. It carries what observers need, nothing more.

## Fire and observe in a ViewModel

Through the event plugin (`core/presentation/api`), so the handler runs on the ViewModel's scope and is
logged:

```kotlin
class CartViewModel(override val plugins: PluginRegistry) : ViewModel(), EventPlugin {
    override val eventHandler = EventHandler()

    init {
        observeEvent<SessionExpired> { clear() }
    }

    fun checkout() {
        fireEvent(CartChanged(count = 0))
    }
}
```

## Fire and observe anywhere else

Inject `EventBus`:

```kotlin
eventBus.fire(SessionExpired)
eventBus.observe<SessionExpired>().collect { … }
```

## What to expect

- **Not state.** An event fired while nobody observes is gone; there is no replay. What a late screen
  must know belongs in a repository or a `RuntimeDataSource`.
- **Subtypes included.** `observe<BaseEvent>()` sees every event; `observe<CartEvent>()` every subtype
  of `CartEvent`.
- **Back-pressure.** A slow subscriber holds up to 64 events before `fire` suspends.

## Testing

`testPlugins(eventBus)` (`core/testing`) builds the real handlers on a bus you pass; share one
`SharedFlowEventBus` between two ViewModels to test one firing and the other observing.
