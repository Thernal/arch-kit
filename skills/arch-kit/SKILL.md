---
name: arch-kit
description: Writes, reviews and debugs ViewModels, screens, use cases and repositories in Compose Multiplatform apps that use arch-kit (packages io.thernal.archkit.core.*; Logic, StoreScope, StoreOwner, StoreFactory, setState, emit, ActionLogic, Policy, Slice, Slot, StateKeeper, CollectEffects, createViewModel, ContentState, UiString, FormField, Failure, safeCall, UseCase, EventBus, storeTest). Use it for any presentation or domain work in such a project, even when arch-kit is not named - a new screen, state and intents, one-off effects, snackbars and navigation, events between features, loading and error states, racing requests and double taps, error mapping, forms and validation, user-facing strings, assisted ViewModel arguments, drafts that survive process death, logic tests; and "No ViewModelResolver installed". Not for projects without arch-kit.
---

# arch-kit

arch-kit is the architecture of a Compose Multiplatform app: domain building blocks (`Failure`, `safeCall`,
use cases, `Debouncer`/`Throttler`, `EventBus`) and an MVI runtime for ViewModels (a plain `Logic` hosted by a
`Store`, `ContentState`, `UiString`, forms, DI-built ViewModels). Guide: https://github.com/Thernal/arch-kit —
`core/presentation/api/README.md`, `core/domain/README.md`, `core/README.md` (why). The architecture — levels, rules, cookbook, migrations — is in this skill: `references/mvi/`.

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
- Repositories return `Result` from `safeCall { }`; failures are `Failure(errors, status, message)`.
- Every user-facing text is a `UiString`; remote data is a `ContentState`.

## 3. Levels — decide before writing, and before moving

**Start at level 1, always.** Then ask in order; the first "yes" is the level. A level is not a ranking: each costs
more code than the one below and pays for itself only past its line. Never mix levels in one logic.

| # | Question | Yes → |
|---|---|---|
| 1 | Can a second request start while the first still runs (search as you type, a follow button per row, a double tap on submit)? | **Level 2** — `ActionLogic`: intent → action → `process` under a keyed `Policy` (`Latest`, `Ignore`, `Queue`, `Parallel`) |
| 2 | Is the screen in exactly one of several modes — three or more flags exclude each other (`isSubmitting`, `isVerifying`, `isLocked`)? | **Level 3** — a sealed state of phases, intents handled per phase, work bound with `whileIn`/`runOn` |
| 3 | Past about 300 lines, or intents in groups that never touch each other's state? | **Level 4** — a root `Logic` with one `Slice` per part, each at its own level |
| — | otherwise | **stay at level 1** |

Hosts, at any level: a part that exists only sometimes (dialog, sheet) and must stop its work when it closes →
`Slot`; a part drawn per list item or on several screens that does its own IO → component ViewModel
(`rememberItemViewModels` + `ItemScope`); logic two screens share or that coordinates repositories → a use case;
a source that matters only while the screen is visible → `observeWhileSubscribed`; input the user would be upset to
lose when the app is killed → `StateKeeper`.

**Moving between levels** (details and steps: [references/mvi/migrations.md](references/mvi/migrations.md)):

| Move | Go when | Come back when |
|---|---|---|
| 1 → 2 | a second request can start while the first runs; a `Job?` field; an `isLoading` guard | only one kind of work is left and it cannot overlap |
| 1/2 → 3 | three or more flags exclude each other, or a handler guards on two flags | one or two modes are left |
| → 4 | ~300 lines, or independent intent groups | one slice or two small ones under ~150 lines |
| slice ⇄ slot | the part exists only sometimes | the part is always shown |
| part ⇄ component VM | repeats per item or appears on several screens, owns its IO | the parent must coordinate it |

How every move works: (1) the screen sees the same `Store<State, Intent, Effect>` before and after — when the state's
shape changes, `mapState { it.toUi() }` keeps the screen untouched; (2) write `storeTest`s on the behaviour *before*
the move and keep them unchanged through it — a test that must change is testing the implementation; (3) one move
per pull request, never with a feature; (4) a line is something you can point to in a diff, not "it feels
complex"; (5) go *down* only when the line is crossed back for good (a removed feature), not for a flag or one
release — a screen exactly on a line stays where it is.

## 4. Tasks

| Task | Read |
|---|---|
| install, graph, root, observers, reporting | [setup.md](references/setup.md) |
| a ViewModel, its logic and screen, effects, events, factories, assisted arguments | [viewmodel.md](references/viewmodel.md) |
| repositories, use cases, errors, events between features, debounce/throttle | [domain.md](references/domain.md) |
| loading/error states, strings, translations, forms and validation | [forms-and-strings.md](references/forms-and-strings.md) |
| logic and ViewModel tests | [testing.md](references/testing.md) |
| the rules of a screen, a review checklist, common mistakes, DI and the size of a ViewModel | [mvi/guide.md](references/mvi/guide.md) |
| a real case — pagination, optimistic update, search as you type, forms, multi-step flow, timers, selection, platform calls, per-item ViewModels, drafts, test recipes | [mvi/cookbook.md](references/mvi/cookbook.md) |
| a screen crossed a level line (up or down), or a part changes host | [mvi/migrations.md](references/mvi/migrations.md) |
| how the store runs: threading and ordering, `isBusy`, the pipeline, slices and slots, process death, what came from Orbit and Decompose | [mvi/reference.md](references/mvi/reference.md) |

## 5. Rules

- Never use `viewModelScope.launch` for a screen's work — `launch { }` in the logic dies with the store and
  reports what escapes.
- Never put a resolved `String` meant for the user in state or effects — a `UiString`.
- Never hold a navigator, a `Context` or a snackbar host in a logic or a ViewModel — emit an effect.
- Model one-off things (a toast, a share sheet, navigation) as effects, never as state flags.
- Never keep a `Job?` or an `isLoading` guard to settle a race: a keyed `Policy` does it (`ActionLogic`) — that is the level 1 → 2 line.
- Never move a screen to another level inside a feature change, and never without `storeTest`s that pass before and after.
- A logic never touches Android, Compose, `viewModelScope` or the DI graph; a ViewModel has no behaviour.
- Never catch `CancellationException`; `safeCall` rethrows it.
- A repository never lets a transport exception out: map it to `Failure` inside `safeCall`.
- Cross-field validation is a `FormRule`, not a validator reading another field's state by hand.

## 6. Verify

```sh
./gradlew build
```
