# MVI reference

How the runtime works and why it is built this way. To write a screen, start with the [guide](guide.md); to
move a screen between levels, read [migrations](migrations.md). This page is what both of them link to for
the details.

The runtime is `core/presentation/api` and `core/presentation/impl` (`StoreHost`), its test DSL is
`core/testing`, and the tests run with `./gradlew build` — every target, including the iOS simulator. The
samples, one per level, are in the cmplayground repository (branch `feat/mvi-patterns-v3`).

## The one contract

Every level has the same outside, `Store<State, Intent, Effect>`: a `StateFlow` of one immutable state,
`postIntent`, and effects delivered once. The screen is written against that and nothing else.

Every level has the same inside too, `Logic<State, Intent, Effect>`. It is a plain class that sees a
`StoreScope`: the state, `reduce`/`setState`, `emit`, and a scope. The logic does not know who hosts it, and
there are five hosts:

| Host | For |
|---|---|
| `store(storeFactory, initial, logic)` in a ViewModel that is a `StoreOwner` | a screen, or a component with its own ViewModel |
| `Slice(logic, get, set, onEffect)` inside a parent logic | one part of a bigger screen, for as long as the screen lives |
| `Slot(get, set, create, onEffect)` inside a parent logic | a part that exists only while it is open: a dialog, a sheet |
| `ItemScope(items, key)` around a list item | a component ViewModel per list item |
| `testStoreFactory().create(scope, initial, logic)`, or `storeTest { }` | a test |

Because of this, moving a piece of logic is a change of host and not a rewrite. That is what keeps migration
between levels cheap. The ViewModel holds no behaviour: it is one line, and the logic is tested without it.

```kotlin
class SearchViewModel(storeFactory: StoreFactory, users: UserRepository) :
    ViewModel(),
    StoreOwner<SearchState, SearchIntent, SearchEffect> {
    override val store = store(storeFactory, SearchState(), SearchLogic(users))
}
```

`StoreOwner` is an interface, named after Android's owners (`LifecycleOwner`, `ViewModelStoreOwner`): the
ViewModel hands out its store and is not one itself. Being an interface, it leaves the superclass free for an
app's own base ViewModel. The screen is given the ViewModel's `.store`.
The extreme case: a report screen's logic, unchanged, runs as a screen and as a sheet.

### The screen sees only what it draws

A logic keeps the state it needs, which is often not what the screen draws: phases, ids, counters.
`store.mapState { it.toUi() }` gives the screen a `Store<Ui, …>` (Orbit's internal and external state). The
sign-in example keeps three different states behind one `Store<SignInUi, …>`, so its screen cannot tell which
level runs. The mapping runs on every read: it must be pure and cheap. Per-component mapping stays in the UI,
with `select { }`. When to use it, what it costs, and how to test through it: [guide, section 11](guide.md#11-internal-state).

### Effects

- **One collector.** Each effect goes to one collector. A second collector at the same time fails at once,
  because two collectors would race for each effect and lose half of them silently.
- **Never dropped.** An effect emitted while no one collects waits for the next collector.
- **Only while the screen is resumed.** `CollectEffects` collects while the lifecycle is at least `RESUMED`
  (`minActiveState`), so a screen in the background does not navigate or show a message; the effect waits in
  the store and is handled when the screen is back. Its block runs in a `CoroutineScope`: suspending in it
  holds the next effect back (a snackbar queue), `launch` lets it through.
- **Handled before the screen can leave.** `CollectEffects` collects on `Dispatchers.Main.immediate`, so an
  effect taken from the store is not lost between the store and the screen.

### How a store runs: `StoreSettings`

These are set once for the app, in the `StoreFactory` its DI provides (`PresentationProvidersModule`):

- `start`:
  - `Lazily` (the default) runs the logic's `onStart` on first use: the first intent, or the first collector.
    A ViewModel created but never shown does no work.
  - `Eagerly` runs it when the store is created.
  - `Manually` runs it only when `start()` is called. Tests use it to look at the state before the start.
- `onError` gets every failure, in an intent or in launched work. By default it **throws**: in tests a bug
  fails the test (`testStoreFactory()` keeps the default). The kit's wiring sets one that reports the failure
  to `FailureReporting`, and the store then keeps working.
- `observers` is a list of `StoreObserver`. Each sees every intent, every change of state (with the `Partial`
  that made it), and every effect, in the order of the list; one that fails is reported to `onError` and the
  others still hear. They are the hook for debug logs and analytics, and the test DSL is built on one. Named
  partials make the logs readable: `SearchPartial.Results`, not `<lambda>`. A test builds its own settings, so
  an app's observers do not run in it.

### Threading and ordering

The store runs on one thread, the main thread, and several rules rest on that:

- **Intents are posted from the main thread.** The running work is kept in plain maps (`ActionRunner`), and
  the synchronous part of an intent runs on the caller's thread.
- **`postIntent` returns when the intent's synchronous part is done:** `toAction` or `onIntent` has run, and
  its `reduce`/`setState` calls are in the state. So a guard in the next intent sees the flag that the one
  before wrote. This holds for any scope, since it does not depend on how the work is launched.
- **Work starts as the scope dispatches it.** A ViewModel's scope is `Dispatchers.Main.immediate`, so launched
  work runs up to its first suspension before `postIntent` returns; a test's scope (`StandardTestDispatcher`)
  runs it at the next `runCurrent()`. Do not rely on that part: **write the flag a guard reads in the
  synchronous part**, before `launch`, as `ReportLogic` writes `isSending`. A flag written inside the
  `launch` is too late for the second tap whenever the work is not started at once.
- **`UNDISPATCHED` start was considered and not adopted.** It would make launched work run up to its first
  suspension on the caller's thread for every scope, but nothing needs it (the guards write in
  the synchronous part), and its cost is real: heavy work before the first suspension would block the caller,
  and a test's timing changes. The rule above is enough.

### Work and binders: `isBusy`

`Store.isBusy` is true while the logic has **work** running: launched work (`launch`), an action's `process`,
the work of a phase (`whileIn`, `runOn`). A **binder** is not work: `observe`, `observeWhileSubscribed` and
`whileSubscribed` run for as long as the store lives, and a store that is busy forever cannot be let go. What
holds a component ViewModel while its request runs is this ([`ItemViewModels`](#component-viewmodels-in-a-list)).

## The levels

| Level | Use it when | Mechanism |
|---|---|---|---|
| **1 · Direct** | No request can overlap another, and there are no exclusive modes | `Logic`: intents call `setState`, a request is `launch`ed by hand |
| **2 · Actions** | Work overlaps: search as you type, double taps, a request per list item | `ActionLogic`: intent → action (parameters gathered) → `process` under a `Policy` → `Partial`s |
| **3 · Phases** | The screen is in exactly one of several phases, and the transitions are the logic | A sealed state of phases; intents handled per phase, work bound with `whileIn` and `runOn` |
| **4 · Slices** | Parts of the screen change for different reasons | A composition-root `Logic` with one `Slice` per part, each slice at its own level |

Component ViewModels are not a level. They are a way of hosting: a component whose logic owns its IO gets
its own ViewModel (see `feed/`).

### Level 3

The phases are a sealed type, the transitions between them are pure functions (`submitted()`,
`answered(result)`, `ticked()`), and the wiring is plain code:

| | How |
|---|---|
| An intent | `when (phase)`, then `when (intent)`; a phase that does not list it ignores it |
| A phase's work | `whileIn(Phase::class) { }` starts on entering, is cancelled on leaving |
| A result | `setState { answered(result) }`, which is ignored if the phase was left |
| Work an intent starts within a phase | `runOn(Phase::class) { }`, cancelled if the phase is left |

`whileIn` restarts its work only on entering the phase, so a phase that updates its own data (an upload's
progress, a countdown) keeps its work running; when the work depends on something inside the phase, it takes
a `key`. The compiler checks the `when` over the sealed phases for a missing one. There is no graph as one
declaration; the transitions file and the `when` are its nearest thing.

## Moving between levels

The lines that say when to move up or down, a recipe for every move, and the sign-in screen written at every
level are in [migrations](migrations.md).

## The pipeline, stage by stage

`Intent → (gather) → Action/Event → process → Partial → reduce → State → render`, with `Effect` beside
`State`.

| Stage | Owns | Without it |
|---|---|---|
| **Intent** | What the user did, in the user's words | The screen decides behaviour (`onClick = { vm.submit(email.trim()) }`) |
| **Gather** (intent → action or event) | Reading the state, collecting the parameters, validating, and the synchronous changes | `process` reads a state that changed while it was suspended. This is the "Event" layer of the original idea: it builds what the action needs |
| **Action / Event** | What to do, as data, with every parameter | No place to name a concurrency rule. Nothing plain to assert on |
| **Policy** (level 2) | How a new action meets a running one with the same key; every policy takes its key | Hand-kept `Job`s and staleness checks |
| **Process** | IO only | — |
| **Partial** | One change, applied in order to the state as it is at that moment | Two results that arrive together overwrite each other. Named partials also show up in logs and are testable on their own; `setState { }` is an anonymous one |
| **Effect** | One-off work: navigation, a message | Kept in state, it runs again on recomposition or when the user comes back |

A stage that adds nothing at a level is skipped. Level 1 goes from intent straight to `setState`.

## Splitting a big ViewModel

A use case is the only split we have today. These are the others, each with its boundary:

| Piece | Holds | Boundary |
|---|---|---|
| **Slice** | One part of the state, its intents, and its work | Never sees its siblings. It can be hosted alone |
| **Slot** | A part that exists only while open | Its work dies when it closes; its outputs go to the parent |
| **Composition root** | Routing, rules that span slices, gathering across slices, and acting on their outputs | No IO of its own. See `CreatePostLogic` |
| **Binder** (`observe(flow) { }`) | A flow from outside (session, settings, event bus) mapped into the state | No decisions. Act2Act's `AppStateBinder` is one |
| **Gatherer** (`checkDraft()`) | Builds an action's parameters from the whole state, and validates them | A pure function, tested alone |
| **Transition** (`answered(result)`) | One move from a phase to the next | A pure function, shared by every wiring of level 3 |
| **Partial** | One reduction | A pure function |
| **Mapper** (`toBodyUi()`) | Logic state → a component's UI state | Lives in the UI layer and is pure |
| **Use case** | Domain orchestration across repositories | No presentation types |
| **Manager** | Runtime state shared across screens | Not in a ViewModel, and not a `StateFlow` in a repository |

### Rules for slices and slots

1. **Children never reference each other.** When one needs something another has, the parent passes it as
   the parameters of an intent (`PublishIntent.Submit(draft)`).
2. **Each part of the state has one writer.** The child writes its own part. The parent writes none of the
   children's parts, except to open or close a slot, or to reset them all.
3. **Rules that span children belong to the parent.** "Nothing is editable while publishing" is one `if` in
   the parent's routing, not a check in each slice.
4. **A child's effects are its outputs, and they go to the parent.** `onEffect` decides: forward one to the
   screen (`emit`), or act on it. When the publish slice says "published", the parent clears the draft and
   then tells the screen. When the report slot says "sent", the feed closes it. This is Decompose's rule: a
   child talks only to its parent. `slice(logic, get, set)` forwards everything as it is.
5. **A child is attached in the parent's `onStart`.** Each child gets a scope of its own inside the
   parent's (Decompose's child lifecycle): detaching a slice or closing a slot cancels its work and nothing
   else. A child that has been closed cannot write into the parent any more.

This is why Act2Act's `RootViewModel` split did not hold. Its coordinators take `context(RootViewModel)`, so
each one sees all of the state and all of the ViewModel. They also hold references to each other
(`pushCoordinator.initialize(routing = routingCoordinator)`), and the order they start in is hidden in
`init`. The split is in name only, and the coupling stays where it was. Rebuilt as slices, each coordinator
owns a part of the root state, its outputs go to the root logic, and the root logic routes between them.

### Component ViewModels in a list

`rememberItemViewModels(keys)` and `ItemScope(items, key)` give every list item a ViewModel store of its own
(Decompose's `childItems`, with a different lifetime). An item's ViewModels live:

- **while the item is on screen;**
- **after it has scrolled away, for as long as their work runs** (`Store.isBusy`): a like that is being sent
  is not cancelled by a scroll, and when it ends the ViewModel is let go;
- **never past the list:** an item that leaves the list is cleared at once, working or not.

So the live ViewModels are the items on screen and those with work in flight, however long the list grows.
The earlier choices each had a flaw: keeping item ViewModels in the screen's store kept every one until the
screen closed (a long, paged feed grew without limit), a store remembered by the item lost its state and
cancelled its request on every scroll, and keeping them while in the list grew with the list.

What comes with a ViewModel that is let go: its state is gone. What its work produced belongs in the list's
data source (a repository, a cache) before it ends, and the item reads it from there when it comes back. That
data source is the data layer's, not this architecture's.

`ItemViewModels` finds an item's stores by looking into its `ViewModelStore` for `StoreOwner`s, so a component
ViewModel must be a `StoreOwner`. Its tests are `ItemViewModelsTest`.

## Work that only matters on screen

`whileSubscribed { }` and `observeWhileSubscribed(flow) { }` (Orbit's `repeatOnSubscription`) run while
someone collects the store's state, and stop a timeout after the last collector leaves. `collectAsState` is
lifecycle-aware, so "someone collects" means the screen is visible. Only collectors of the state that the screen
sees count: the store's own (a keeper, `whileIn`, `runOn`) do not, or the work would never stop. Use them for live counts, a location, or
a socket. Use plain `observe` for what must stay current even off screen: a session, or settings.

## Surviving process death

`storeFactory.keeper(handle, key, serializer, save, restore)` (Decompose's `StateKeeper`, on Jetpack's handle and
kotlinx-serialization) keeps a part of the state. A feature sees only the `StateKeeper` interface; the factory
builds the implementation, and the host saves and restores. The store starts from the saved part and saves it again
whenever it changes. The first change is written at once, and those that follow within 300 ms are merged into
one write of the last value, so typing is not encoded on every key:

- **Keep what the user would be upset to lose:** a draft, a selection, a step.
- **Never keep loaded data:** it is fetched again.
- **Never keep a busy phase.** The work behind it died with the process. A create-post screen keeps the
  draft, and a restored screen starts in `Idle`, not in `Uploading`.
- A saved value that no longer decodes, after an app update, is dropped.

## Testing

`storeTest(initial, logic) { }` (from `core/testing`, after Orbit's test DSL) records every change of state and
every effect, in order, and the test takes them one at a time:

```kotlin
storeTest(LikeState(isLiked = false, count = 7), LikeLogic(FAILING_POST, posts)) {
    send(LikeIntent.Toggle)
    expectState { copy(isLiked = true, count = 8) }
    advance(1_000)
    expectState { copy(isLiked = false, count = 7) }
    expectEffect(LikeEffect.Failed)
}
```

- A state is asserted as a change from the one before, so the test reads like the reducer it checks.
- Nothing may be left at the end: a change the test did not expect fails it, and so does a failure the
  store reported.
- Time is the test's: `advance(millis)` moves it.
- `subscribe()` stands for a visible screen.
- `storeTest(start = false)` checks the state before `onStart` runs.

For behaviour shared by several implementations, assert on the mapped store instead, as
`SignInBehaviourTest` does.

## State style

There is one immutable state per store, and it changes only through partials. The UI gets component states
from mappers built on `select { }` (ArenaGo's `StateSelection`), so each component recomposes only for its
own part, and a component that later gets its own ViewModel already has its own state type.

**Act2Act's `StateHolder` is not MVI.** It is a mutable class of independent `stateOf` fields that any code
in the ViewModel can `set`:

- There is no single transition point. Logging, replaying, and tracking down "who changed this" are not
  possible.
- The state is not a value. It cannot be compared in a test, saved, or diffed.
- A change to several fields at once needs `snapshot { }`, and forgetting it produces intermediate states.

Its benefit is per-field recomposition, and immutable state with mappers gives the same benefit. New code
should use one immutable state and mappers.

## What we took from Orbit and Decompose

Neither library is adopted whole, and the state-machine library we tried (dev.klitsie.statemachine) is not used: plain phases (`whileIn`, `runOn`) did the same with less code and no dependency. Orbit's intents run with no concurrency rule, which is weaker than
`ActionLogic`'s policies. Decompose replaces navigation and the ViewModel together. Their good ideas fit the
runtime as they are:

| Idea | From | Here |
|---|---|---|
| Work bound to a phase, cancelled on leaving it | Orbit `runOn` | `whileIn`, `runOn` |
| Internal and external state | Orbit `transformState` | `mapState` |
| One effect collector, effects cached while none collects | Orbit `SideEffectMode` | `Store.effects` |
| Start on first use | Orbit lazy `onCreate` | `Start.Lazily` |
| Work only while subscribed | Orbit `repeatOnSubscription` | `whileSubscribed`, `observeWhileSubscribed` |
| Test DSL | orbit-test | `core/testing`: `storeTest { }` |
| Store settings: error handler | Orbit `RealSettings` | `StoreSettings`: `onError` that fails fast, plus a list of observers that Orbit does not have |
| A child talks only to its parent | Decompose components | Slice and slot outputs (`onEffect`) |
| Child lifecycle | Decompose `childContext` | A scope per slice; `detach()` |
| Children that come and go | Decompose `childSlot` | `Slot`, hosting a `Hosted` |
| Per-item lifecycle | Decompose `childItems` | `ItemViewModels`, `ItemScope`: on screen or busy, not for as long as the item is in the list |
| State that survives process death | Essenty `StateKeeper` | `StateKeeper` on `SavedStateHandle` |

Left out on purpose:

- **Decompose's `JetpackComponentContext`.** We are not hosting Decompose components.
- **`childStack` and Decompose's navigation.** Navigation stays with Navigation3 and its entries' ViewModels,
  whose back stack is already state.
- **Orbit's container and intent model.** `Logic` with policies already covers them.

## Considered and not adopted: removing the `Scope` alias

Every logic repeats its scope type: `private typealias Scope = StoreScope<State, Effect>` once, and `Scope.` on
each handler. We tried to remove that. Each variant below was compiled with Kotlin 2.4.10, and the ones that
compile were run against the report screen's three tests. **We kept the alias.** This section records why, so
the question is not opened again from nothing.

First, the idea that looks natural does not exist in the language:

- **An alias inherited from `Logic<S, I, E>`.** A nested `typealias` cannot see the outer type parameters
  (`typealias Scope = StoreScope<S, E>` inside a generic class: `Unresolved reference 'S'`), and an alias
  does not bind to a subclass's type arguments. There is nothing to inherit.
- **A context parameter on the class** (`context(scope: StoreScope<S, E>) class …`) is rejected:
  `Context parameters on classes are unsupported`. A context parameter on each function repeats the type
  just as `Scope.` does, and is longer by a line.

What does compile:

| | Alias (kept) | E: scope in a base class | H: base class is the `StoreScope` | G: a DSL |
|---|---|---|---|---|
| Alias line per class | 1 | none | none | none |
| `Scope.` on each handler | yes | none | none | none |
| Handlers | extensions on `Scope` | plain members | plain members | local functions in a lambda |
| Hidden mutable state | none: the scope is a parameter | **yes**: a field set on each call | **yes**: a field set on each call | none |
| Existing extensions (`observe`, `whileIn`, …) | work | rewritten in the base class | work | work |
| `ActionLogic` | work | needs a second base class | needs a second base class | not applicable |
| Public surface of the logic | `onIntent` | `onIntent` | **`state`, `reduce`, `emit` become public** | a function |
| Fits constructor DI and a nested `Factory` | yes | yes | yes | no: it is not a class |

Sketches, with the report screen as the example (E and H differ only in the base class):

```kotlin
// E / H: handlers are plain members
class ReportLogic(…) : ScopedLogic<ReportState, ReportIntent, ReportEffect>() {
    override fun onIntent(intent: ReportIntent) { … }
    private fun send() { … setState { … } … launch { … } }
}

// G: the scope is the lambda's receiver
fun reportLogic(postId: String, posts: PostRepository) =
    logic<ReportState, ReportIntent, ReportEffect> { intent -> … }
```

Why the alias stays:

- The saving is small: one line per class and one word per handler.
- E and H keep the scope in a field. A logic shared by two hosts by mistake would write into the wrong
  store without any error. With the alias the scope is a parameter and nothing is kept.
- E, H and G each leave `ActionLogic` out, so the code would have two styles for
  handlers.
- G is not a class, which breaks the assisted factory in [the DI section](guide.md#12-dependency-injection-and-the-size-of-a-viewmodel).

If the repetition ever hurts enough, **H** is the one to take: a separate, optional `ScopedLogic` for plain
`Logic`, with the public `StoreScope` members written down as its cost. Nothing else would change.

## What is arch-kit's and what is the runtime's

The runtime is arch-kit's presentation layer. Level 1 is what arch-kit's ViewModels were: `setState { copy() }`
has the same name on purpose.

- `Logic` + `StoreScope` replace "the ViewModel implements the plugins"; `StoreFactory` replaces the plugin
  registry.
- `ActionLogic`/`Policy`, phases, `Slice`, `Slot`, `StateKeeper`, `ItemViewModels` and the test DSL are the
  additions.
- `ContentState`, `UiString`, forms, `Failure`, `safeCall`, `EventBus` and the DI factories stay as they were,
  and a `Logic` uses them through its constructor: a bus to `observe`, a use case, a lookup.
- **Messages and navigation are not in the kit.** `MessageEffect` and `NavEffect` were arch-kit's, handled by
  composition locals the root installed; a ViewModel could reach a snackbar and a navigator without its effect
  type saying so. A feature now declares `Message(UiString)` and `Navigation(process: Navigator.() -> Unit)` in
  its own effect type, and its screen handles them. A navigation lambda is not comparable: a test runs it on a
  navigator and asserts on the navigator.

## Open questions

- Can the "lines" be Detekt rules? Counting flags and guards, the size of a `Logic`, and slice-to-slice
  references all look checkable.
- Should `Start.Lazily` stay the default once real screens use it? A screen that posts an intent from its
  ViewModel's constructor starts the logic early, which is harmless; nothing has needed `Eagerly` yet.
- The saved state of a screen is the Navigation3 entry's `SavedStateHandle`; that wiring is not tried in an app yet.
- Does the keeper's `SavedStateHandle` survive the app being killed on iOS? The handle and its serialization
  are multiplatform, but nothing here shows that the platform writes it to disk and gives it back. Try it on a
  simulator before relying on it; if it does not, a draft that must outlive the process there goes to a
  storage the app owns (DataStore, settings) behind an interface, and not into the handle.
- `ItemViewModels` finds an item's stores through `ViewModelStore.keys()` and `get`, which Android marks as
  library-internal. They compile on every target here; a release of lifecycle that hides them would mean the
  component ViewModel registers itself instead.
- The rule "write the flag a guard reads in the synchronous part" is a convention. A lint (a `setState` as the
  first statement of a `launch` that sits behind a guard on the same field) could check it.
- The runtime is one module (`mvi/`) that depends on coroutines, lifecycle, savedstate, serialization and
  Compose. Splitting a core that needs coroutines only from the ViewModel and Compose adapters is decided and
  not done yet.
