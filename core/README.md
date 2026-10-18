# arch — design

Why the kit has the shape it has. The kit is ArenaGo's `core/domain`, `core/event` and
`core/presentation` ported to Compose Multiplatform, with the choices where Act2Act differed decided
explicitly (epic decisions D40–D44).

## A ViewModel owns a store; behaviour is a plain class

The first version made a ViewModel implement plugins (`StatePlugin`, `IntentPlugin`, `EffectPlugin`) whose
handlers compiled only inside it, so "state changes only in a ViewModel" was a compile error. That kept
every screen's behaviour in a class that needs a ViewModel to run, and gave a big screen nowhere to go
but a bigger ViewModel. The runtime is now cmplayground's `mvi-patterns-v3`: a `Logic` — a plain class —
holds all of a screen's behaviour, a `Store` hosts it, and the ViewModel is one line, `StoreOwner` with
`override val store = store(storeFactory, State(), Logic(...))`.

The rule survives in a different place. `setState`, `emit`, `launch` and `observe` are members of
`StoreScope`, which a logic receives only inside `onStart` and `onIntent`: there is no way to change a state
from a screen, a repository or a test body, and a logic needs no ViewModel to be tested, hosted as a
`Slice` of a bigger screen, or opened as a `Slot` (a dialog). Four levels — direct changes, keyed
actions (`ActionLogic` with `Policy`), phases (`whileIn`, `runOn`), slices — let a screen grow without its
screen code changing; [`skills/arch-kit/references/mvi/`](../skills/arch-kit/references/mvi/README.md) has the rules for each move.

`StoreFactory` replaces the plugin registry as the one seam: it builds every store with the app's
`StoreSettings`, so logging (`StoreObserver`), failure reporting and a test's fail-fast default change one
binding, not every ViewModel.

## Effects: delivered once, and the kit does not know what they are

A store's effects go through an unbounded channel to exactly one collector: an effect is not shown twice
after a rotation, one emitted while no screen collects waits for the next collector, and a second
collector fails at once instead of racing the first for half the effects. Intents run in order, on the
caller's thread.

Messages and navigation are not effects of the kit. The first version shipped `MessageEffect` and
`NavEffect<N>` and installed handlers for them at the root; that hid a dependency — a ViewModel could
reach a navigator and a snackbar without its effect type saying so — and made every feature depend on
types the kit owned. Now a feature declares what it can ask of its screen, `Message(UiString)` and
`Navigation(process: Navigator.() -> Unit)` beside its own effects, and its screen handles them where the
snackbar host and the navigator are. The kit names no design system and no navigator, and no handler is
installed at the root. A navigation lambda is not comparable, so a test runs it on a navigator and asserts
on the navigator's state.

## An observer sees chains of work, not only events

A log of intents, states and effects in the order they happen does not say which intent a late state belongs
to: a search that answers after the next keystroke reads as the keystroke's. So the store opens a chain
(`StoreTrace`) for every intent and for the start, and hands the logic a scope of that chain. The trace rides
on the scope, not on the coroutine context: `reduce` and `emit` do not suspend, so they cannot read a context,
while a coroutine the logic launches captures the scope it was launched from — its late `reduce` is still the
intent's.

A child's scope is created once, when it is attached or opened, so it cannot know who calls it. The store
knows: it records the chain it handles on its thread (`TracedScope.handling`), and a slice's or a slot's intent
joins that one. An effect a child sends up is handled in the child's chain, so the parent's handler, and a
sibling it posts to, stay in it too.

A phase's work is a chain of its own, since it outlives the intent that entered the phase and restarts with it.
Its parent is the chain whose change of state the work woke up to. Phases come from a `StateFlow`, so when two
changes land before the work's coroutine runs, that is the later one.

## Errors do not crash the screen

A failure that escapes a logic — in an intent or in launched work — goes to the store's `onError`, which the
graph sets to `FailureReporting`, and the store keeps working: a forgotten `try` is a report, not a crash.
`safeCall` returns a `Result` with a `Failure` and reports it; what the user sees of it is the logic's
decision, an effect or a `ContentState.Error`. In a test the default is the opposite: `onError` throws, so a
bug fails the test.

## Strings are resolved where they are drawn

State, effects and form errors carry `UiString`, never a resolved string: a ViewModel does not touch
resources, and a test compares values. ArenaGo's `@StringRes` is Compose Resources' `StringResource` here.
The kit's own texts — validator messages, failure fallbacks — are `Localized(key, fallback)`: an app with
key-based translations resolves `validation.email` through its `StringLookup`, every other app shows the
English fallback. The kit ships no resource files: resources in a renamed, copied module merge badly; keys
do not.

## Forms have two kinds of rules

A field validator sees one value; `+` combines them, first message wins. What spans fields — "repeat must
match", "end after start" — is a `FormRule`, which returns the form with errors set where they belong. A
server's field errors land on fields the same way (`withServerErrors`), and the ones the form does not show
come back for a message.

## The event bus has one stream

ArenaGo kept one flow per event class, created lazily without a lock, so an observer of a supertype never
saw its subtypes. Here there is one shared flow and each observer filters by type.

## One concept per file, packages by role

Each file holds one type, or one type and the composition local that installs it; a type's extensions get
a file of their own beside it (`StoreScopeState.kt`, `ContentStateExtensions.kt`), so a contract reads without
its helpers and a helper is found by name. Packages say what a part is for, in two levels: the area
(`store`, `logic`, `compose`, `form`, `viewmodel`) and, inside it, the role (`store.scope` is what a logic
does on its store, `store.state` how a state is changed and mapped, `store.keeper` persistence;
`logic.action`, `logic.phase`, `logic.slice`, `logic.slot` are the levels; `compose.state`, `compose.effect`
and `compose.item` are the screen's three concerns). `api` holds what a feature calls, implements or extends
— a feature module depends on nothing else — and `impl` what only a host needs: `StoreHost`, the one class
that runs a logic, `StoreFactoryImpl` and the Metro resolver. The rule: contracts, interfaces and configuration a feature, a test or the
app's graph writes (`Store`, `StoreFactory`, `StoreSettings`, `StoreObserver`) are `api`; only what runs is `impl`. A class
a feature extends or constructs (`ActionLogic`, `Slice`, `Slot`) has to be in `api` with its machinery, because
a feature cannot reach `impl`; Kotlin `internal` keeps that machinery (`ActionRunner`, `ChildScope`) out of
sight. The presentation modules are the presentation layer by name, so their packages start right under the
module (`core.presentation.api.store`), never `core.presentation.api.presentation` — the layer Detekt rule
reads the layer off the module name and reports a repeated one. `composition` holds what every feature builds
on — `ComponentRenderer` and `ContributedLocals`.

`ViewModelResolver` is not a renderer: it draws nothing, it hands a composition its ViewModel. The
`Renderer` suffix is kept for contracts that do draw, which is what lets `ComponentRenderer` mean one
thing.

## What changed besides

- `FlowUseCase.execute` and `safeCallFlow` are not `suspend`: a flow is cold, and Detekt is right that a
  suspending function returning one is a smell.
- Logging goes to `StoreObserver`s the app contributes, and failures to a `FailureReporter` — the kit
  depends on no console.
- Paging entities stay with paging-kit; `PreventScreenshot`, list helpers and `NavigationState` (Android- or
  navigator-specific) are not here.
