# Open questions in the runtime

**Status:** open

- **Does the keeper's `SavedStateHandle` survive the app being killed on iOS?** The handle and its
  serialization are multiplatform, but nothing here shows that the platform writes it to disk and gives it
  back. Try a draft on a simulator before relying on it; if it does not survive, a draft that must outlive
  the process there goes to a storage the app owns, behind an interface, and not into the handle.
- **`ItemViewModels` reads `ViewModelStore.keys()` and `get`,** which Android marks as library-internal. They
  compile on every target here; a lifecycle release that hides them would mean the component ViewModel
  registers itself instead.
- **Lints.** The rules of the guide that a tool could check: a flag a guard reads is written before `launch`, a
  `Job?` field in a logic, the size of a `Logic`, a slice referring to another slice. Detekt rules next to the
  kit's own.
- **A navigator for ViewModel tests.** A navigation effect holds a lambda over the app's navigator; a test runs
  it and asserts on the navigator. nav-kit has a real `BackStackNavigator` but no helper that builds one
  without a host — a `testNavigator()` there would save every app writing its own fake.
- **Splitting the runtime** so that a core needing coroutines only is separate from the lifecycle and Compose
  adapters. Not done; `api` depends on both today.
