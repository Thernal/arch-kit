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

## Fire and observe in a Logic

A logic takes the `EventBus` in its constructor, observes in `onStart` — on the store's scope, so it stops
with the ViewModel — and fires from its own work:

```kotlin
class CartLogic(private val bus: EventBus) : Logic<CartState, CartIntent, Nothing> {
    override fun StoreScope<CartState, Nothing>.onStart() {
        observe(bus.observe<SessionExpired>()) { setState { CartState() } }
    }

    override fun StoreScope<CartState, Nothing>.onIntent(intent: CartIntent) {
        when (intent) {
            CartIntent.Checkout -> launch { bus.fire(CartChanged(count = 0)) }
        }
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

Share one `SharedFlowEventBus` between two logics hosted with `testStoreFactory()` (`core/testing`) to test one
firing and the other observing.
