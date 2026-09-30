# Testing ViewModels

```kotlin
@Test
fun submitShowsServerErrors() = runViewModelTest {
    val viewModel = SignUpViewModel(plugins = testPlugins(), signUp = { throw rejected })
    val effects = viewModel.recordEffects(backgroundScope)

    viewModel.postIntent(SignUpIntent.EmailChanged("ada@example.com"))
    viewModel.postIntent(SignUpIntent.Submit)
    testScheduler.advanceUntilIdle()

    assertNotNull(viewModel.state.email.error)
    assertIs<MessageEffect>(effects.single())
}
```

- `testPlugins(eventBus)` — the real handlers without DI; share one `SharedFlowEventBus` between two
  ViewModels to test events.
- `runViewModelTest` — `Dispatchers.Main` (`viewModelScope`) is the test dispatcher; unconfined, so an intent
  runs as it is posted; `advanceUntilIdle()` for `launch`ed work and delays.
- `recordEffects(backgroundScope)` — every effect sent from then on.
- A `NavEffect`: `(effect as NavEffect<Router>).command(fakeRouter)` and assert on the fake.
- Test the stateless screen content with previews/screenshot tests; the ViewModel with these.
