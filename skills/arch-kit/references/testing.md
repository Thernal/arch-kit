# Testing logics and ViewModels

```kotlin
@Test
fun submitShowsServerErrors() = runTest {
    storeTest(SignUpState(), SignUpLogic(SharedFlowEventBus()) { throw rejected }) {
        send(SignUpIntent.EmailChanged("ada@example.com"), SignUpIntent.Submit)
        expectState { copy(email = email.update(TextFieldValue("ada@example.com"))) }
        expectState { copy(profile = ContentState.Loading()) }
        assertNotNull(nextState().email.error)
        assertIs<SignUpEffect.Message>(nextEffect())
    }
}
```

- `storeTest(initial, logic) { }` records every state change and effect in order. `expectState { copy(…) }` is
  the change from the state before; `expectEffect(e)`, `nextState()`/`nextEffect()` for assertions that are not
  equality; `skip`/`skipAll` for what the test does not care about. A change the test did not take fails it.
- Time is the test's: `advance(millis)`; `send` runs everything due. `subscribe()` is a visible screen.
- A navigation effect is a lambda: `(nextEffect() as HomeEffect.Navigation).process(navigator)`, then assert on
  the navigator (nav-kit's real one, or a fake with a list of calls).
- `testStoreFactory()` — the real `StoreFactory` for a ViewModel under test; `runViewModelTest` makes
  `Dispatchers.Main`, which `viewModelScope` runs on, the test's dispatcher.
- Two logics on one `SharedFlowEventBus` test one firing and the other observing.
- A logic is tested alone; the stateless screen content with previews/screenshot tests.
