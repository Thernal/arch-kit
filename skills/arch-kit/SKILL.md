---
name: arch-kit
description: Writes, reviews and debugs ViewModels, screens, use cases and repositories in Compose Multiplatform apps that use arch-kit (packages io.thernal.archkit.core.*; Logic, StoreScope, StoreOwner, StoreFactory, setState, emit, ActionLogic, Policy, Slice, Slot, StateKeeper, CollectEffects, createViewModel, ContentState, UiString, FormField, Failure, safeCall, UseCase, EventBus, storeTest). Use it for any presentation or domain work in such a project, even when arch-kit is not named - a new screen, state and intents, one-off effects, snackbars and navigation, events between features, loading and error states, racing requests and double taps, error mapping, forms and validation, user-facing strings, assisted ViewModel arguments, drafts that survive process death, logic tests; and "No ViewModelResolver installed". Not for projects without arch-kit.
---

# arch-kit

arch-kit is the architecture of a Compose Multiplatform app: domain building blocks (`Failure`, `safeCall`,
use cases, `Debouncer`/`Throttler`, `EventBus`) and an MVI runtime for ViewModels (a plain `Logic` hosted by a
`Store`, `ContentState`, `UiString`, forms, DI-built ViewModels). Guide: https://github.com/Thernal/arch-kit —
`core/presentation/api/README.md`, `docs/mvi/` (the architecture), `core/domain/README.md`, `core/README.md` (why).

## 1. Orient first

```sh
grep -rln --include=*.kt -e "StoreOwner<" . | head                          # existing ViewModels — copy their shape
grep -rn --include=*.kt -e "CollectEffects" .                               # how screens handle effects, messages, navigation
grep -rn --include=*.kt -e "LocalStringLookup" .                            # how keys are translated
grep -rn --include=*.kt -e "FailureReporting.reporter" -e ": StoreObserver" .
grep -rn --include=*.kt -e "fun .*toFailure" .                              # the transport → Failure mapping
```

Nothing installed → [references/setup.md](references/setup.md). **Taken as a kit?** A `kits.lock` naming
`arch-kit` means the modules were copied renamed with skill-manager — this skill too. `skillctl.sh kit status
arch-kit` shows what moved upstream; offer `kit update arch-kit` rather than hand edits.

## 2. The model

- A screen has a **state** (one immutable value), **intents** (what the user did), **effects** (one-off
  outputs) and one **`Logic`** class that turns intents into states and effects. A logic is a plain class:
  dependencies through the constructor, no ViewModel, no Android, no Compose.
- The ViewModel is `ViewModel(), StoreOwner<S, I, E>` with `override val store = store(storeFactory, State(),
  Logic(...))` and nothing else.
- Inside a logic (`StoreScope`): `setState { copy(…) }`, `state`, `emit(effect)`, `launch { }`,
  `observe(flow) { }`, `safeCall { }`. These exist only inside `onStart`/`onIntent`.
- The screen: `createViewModel()`, `store.collectAsState()`, `store.postIntent(…)`, `store.CollectEffects { }`.
- **Messages and navigation are not the kit's.** A feature's effect type declares them
  (`Message(UiString)`, `Navigation(process: Navigator.() -> Unit)`); its screen handles them.
- **Levels.** Start at the simplest, `Logic`. Overlapping work (search as you type, double taps) → `ActionLogic`
  with a keyed `Policy`; exclusive modes → a sealed state with `whileIn`/`runOn`; parts that change for
  different reasons → `Slice`; a dialog with logic → `Slot`. `docs/mvi/guide.md` says when; `migrations.md`
  how.
- Repositories return `Result` from `safeCall { }`; failures are `Failure(errors, status, message)`.
- Every user-facing text is a `UiString`; remote data is a `ContentState`.

## 3. Tasks

| Task | Read |
|---|---|
| install, graph, root, observers, reporting | [setup.md](references/setup.md) |
| a ViewModel, its logic and screen, effects, events, factories, assisted arguments | [viewmodel.md](references/viewmodel.md) |
| repositories, use cases, errors, events between features, debounce/throttle | [domain.md](references/domain.md) |
| loading/error states, strings, translations, forms and validation | [forms-and-strings.md](references/forms-and-strings.md) |
| logic and ViewModel tests | [testing.md](references/testing.md) |
| a real case — pagination, optimistic update, search, multi-step flow, timers, drafts — or a screen that outgrew its level | `docs/mvi/cookbook.md`, `docs/mvi/migrations.md` in the kit repository |

## 4. Rules

- Never use `viewModelScope.launch` for a screen's work — `launch { }` in the logic dies with the store and
  reports what escapes.
- Never put a resolved `String` meant for the user in state or effects — a `UiString`.
- Never hold a navigator, a `Context` or a snackbar host in a logic or a ViewModel — emit an effect.
- Model one-off things (a toast, a share sheet, navigation) as effects, never as state flags.
- Never keep a `Job?` or an `isLoading` guard to settle a race: a keyed `Policy` does it (`ActionLogic`).
- A logic never touches Android, Compose, `viewModelScope` or the DI graph; a ViewModel has no behaviour.
- Never catch `CancellationException`; `safeCall` rethrows it.
- A repository never lets a transport exception out: map it to `Failure` inside `safeCall`.
- Cross-field validation is a `FormRule`, not a validator reading another field's state by hand.

## 5. Verify

```sh
./gradlew build
```
