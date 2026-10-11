# MVI guide

How to write a screen with this architecture, for a developer who sees it for the first time. Read it top to
bottom once. After that, sections 3, 4 and 9 are the ones you come back to.

The architecture has many parts: levels, policies, phases, slices, slots and item stores. **A new screen
needs almost none of them.** Every screen starts at level 1, which is a data class, a sealed interface and
one class with a `when`. The other parts exist for the day a screen outgrows that, and
[migrations](migrations.md) says when that day has come.

Contents:

1. [The model](#1-the-model)
2. [Your first screen](#2-your-first-screen)
3. [Choosing a level](#3-choosing-a-level)
4. [The rules](#4-the-rules)
5. [Screens that need fewer than three types](#5-screens-that-need-fewer-than-three-types)
6. [Keeping the generics quiet](#6-keeping-the-generics-quiet)
7. [Testing](#7-testing)
8. [Names and files](#8-names-and-files)
9. [Review checklist](#9-review-checklist)
10. [Common mistakes](#10-common-mistakes)
11. [Internal state: what the logic keeps and the screen does not draw](#11-internal-state)
12. [Dependency injection and the size of a ViewModel](#12-dependency-injection-and-the-size-of-a-viewmodel)

Cases that come up in real screens (pagination, optimistic updates, forms, shared state, results from other
screens, and more) are in the [cookbook](cookbook.md).

## 1. The model

```
              postIntent(intent)
  Screen ────────────────────────▶ Store ──▶ Logic.onIntent
    ▲                                            │
    │  collectAsState()                          ├── setState { copy(…) } ──▶ State ───┐
    └────────────────────────────────────────────┼─────────────────────────────────────┘
    ▲                                            │
    │  CollectEffects { }, each effect once      └── emit(effect) ──────────▶ Effects ─┐
    └──────────────────────────────────────────────────────────────────────────────────┘
```

A screen has three types and one class:

| Piece | What it is | Example |
|---|---|---|
| **State** | Everything the screen draws, as one immutable value | `data class ReportState(val reason: ReportReason?, val isSending: Boolean)` |
| **Intent** | What the user did, in the user's words | `ReportIntent.Send`, `ReportIntent.EditComment(text)` |
| **Effect** | Something to do once: navigate, show a message | `ReportEffect.Sent` |
| **Logic** | The behaviour: takes intents, changes the state, emits effects | `class ReportLogic : Logic<ReportState, ReportIntent, ReportEffect>` |

Around them, two pieces that hold no behaviour:

- **The Store** runs a logic. It holds the state, delivers the effects, and hands every intent to the logic
  in order. You never write one. You get it from `store(storeFactory, initial, logic)`.
- **The ViewModel** keeps the store alive across configuration changes. It is a `StoreOwner`, and its body is
  one line.

The words you will meet later, all of them optional at the start:

| Word | Meaning | First needed at |
|---|---|---|
| Partial | One named change of the state, with its reduction beside it | Level 2, or whenever logs need names |
| Action | What to do about an intent, with every parameter gathered | Level 2 |
| Policy | What happens when an action starts while one with the same key runs: Parallel, Latest, Ignore, Queue. Each takes its key: `Policy.Latest("search")` | Level 2 |
| Phase | One of the exclusive modes a screen is in, as a sealed type | Level 3 |
| Slice | A part of a big screen with its own logic | Level 4 |
| Slot | A part that exists only while open: a dialog, a sheet | When a dialog has logic of its own |
| Component ViewModel | A part with its own ViewModel, such as a like button per card | When a part repeats, or appears on several screens |
| Keeper | Saves part of the state across process death | When losing the input would upset the user |

## 2. Your first screen

A report screen is a complete level 1 screen. It is four files, one concern each.

```
report/
  ReportContract.kt   State, Intent, Effect
  ReportLogic.kt      the logic
  ReportViewModel.kt  the ViewModel: one line
  ReportScreen.kt     the screen: a route that takes the store, and stateless content
commonTest/…/report/
  ReportLogicTest.kt
```

### Step 1. The contract

```kotlin
data class ReportState(
    val reason: ReportReason? = null,
    val comment: String = "",
    val isSending: Boolean = false,
) {
    val canSend: Boolean
        get() = reason != null && (reason != ReportReason.Other || comment.isNotBlank()) && !isSending
}

sealed interface ReportIntent {
    data class SelectReason(val reason: ReportReason) : ReportIntent

    data class EditComment(val text: String) : ReportIntent

    data object Send : ReportIntent
}

sealed interface ReportEffect {
    data object Sent : ReportEffect

    data class Failed(val message: UiString) : ReportEffect
}
```

- Every field has a default, so `ReportState()` is the initial state.
- `canSend` is computed from other fields, so it is a property and not a field. A field could disagree with
  the others.
- Intents carry the user's raw input (`text`, not `text.trim()`). The logic decides what to do with it.

### Step 2. The logic

```kotlin
class ReportLogic(
    private val postId: String,
    private val posts: PostRepository,
) : Logic<ReportState, ReportIntent, ReportEffect> {
    private typealias Scope = StoreScope<ReportState, ReportEffect>

    override fun Scope.onIntent(intent: ReportIntent) {
        when (intent) {
            is ReportIntent.SelectReason -> setState { copy(reason = intent.reason) }
            is ReportIntent.EditComment -> setState { copy(comment = intent.text) }
            ReportIntent.Send -> send()
        }
    }

    private fun Scope.send() {
        val reason = state.reason
        if (!state.canSend || reason == null) return
        val comment = state.comment.trim()              // gathered before anything suspends
        setState { copy(isSending = true) }
        launch {
            safeCall { posts.report(postId = postId, reason = reason, comment = comment) }
                .onSuccess { emit(ReportEffect.Sent) }
                .onFailure { failure -> emit(ReportEffect.Failed(failure.toUiString())) }
            setState { copy(isSending = false) }
        }
    }
}
```

The alias on the first line names the scope once for the class (see [section 6](#6-keeping-the-generics-quiet)).
Inside `onIntent` you have the `StoreScope`, which provides:

- `state`: the state now;
- `setState { copy(…) }`: a change;
- `emit(effect)`: an effect;
- `launch { }`: work that dies with the store.

You also have `safeCall { }` (from `core/domain`), a `Result` whose failure is a reported `Failure` and that never swallows a cancellation. Dependencies come in through the
constructor. Nothing here knows about Android, Compose or the ViewModel.

### Step 3. The ViewModel

```kotlin
class ReportViewModel(
    storeFactory: StoreFactory,
    postId: String,
    posts: PostRepository,
) : ViewModel(),
    StoreOwner<ReportState, ReportIntent, ReportEffect> {
    override val store = store(storeFactory, ReportState(), ReportLogic(postId, posts))
}
```

That is the whole class, and it stays this way. If you are about to add a function or a field to it, the
code belongs in the logic. `ViewModel()` can be your app's own base ViewModel instead: `StoreOwner` is an
interface.

### Step 4. The screen

```kotlin
@Composable
fun ReportScreen(
    store: Store<ReportState, ReportIntent, ReportEffect>,
    snackbar: SnackbarHostState,
    onDone: () -> Unit,
) {
    store.CollectEffects { effect ->
        when (effect) {
            ReportEffect.Sent -> onDone()
            is ReportEffect.Failed -> snackbar.showSnackbar(effect.message.load(lookup))
        }
    }
    ReportContent(state = store.collectAsState(), postIntent = store::postIntent)
}

@Composable
private fun ReportContent(state: State<ReportState>, postIntent: (ReportIntent) -> Unit) {
    val reason by state.select { it.reason }       // recomposes only when the reason changes
    // …
}
```

- Only this first composable, the route, sees the `Store`. Everything below it gets a `State` (narrowed with
  `select { }`) and a `postIntent` lambda, so it can be previewed and reused without a store.
- Effects are collected once, here, with `CollectEffects`.

### Step 5. Wiring it in

```kotlin
ReportScreen(viewModel { ReportViewModel(postId, posts) }.store, snackbar, onDone = navigator::back)
```

### Step 6. The test

```kotlin
private val posts = FakePostRepository(latency = 1_000)

@Test
fun aSentReportSaysSo() = runTest {
    storeTest(ReportState(), ReportLogic("p1", posts)) {
        send(ReportIntent.SelectReason(ReportReason.Spam))
        expectState { copy(reason = ReportReason.Spam) }
        send(ReportIntent.Send)
        expectState { copy(isSending = true) }
        advance(1_000)
        expectEffect(ReportEffect.Sent)
        expectState { copy(isSending = false) }
    }
}
```

The test needs no ViewModel and no Compose. It checks every change in order, and it fails if a change
happens that it did not expect. `ReportLogicTest` also covers the failure and the ignored send. More in
[Testing](#7-testing).

## 3. Choosing a level

**Start at level 1. Always.** Then answer these questions in order. The first "yes" says where the screen
goes. [Migrations](migrations.md) has the exact signals and the steps for each move.

| # | Question | Yes → |
|---|---|---|
| 1 | Can a second request start while the first is still running? Search as you type, a follow button per row, a double tap on submit. | **Level 2**: actions under policies |
| 2 | Is the screen in exactly one of several modes, so that three or more flags exclude each other? `isSubmitting`, `isVerifying`, `isLocked`, `step`. | **Level 3**: phases |
| 3 | Is the logic past about 300 lines, or do its intents fall into groups that never touch each other's state? | **Level 4**: slices |
| — | Otherwise | **Stay at level 1** |

Some questions are not about the level but about where a piece of logic is hosted. They can come up at any
level:

| Question | Yes → |
|---|---|
| Does a part exist only sometimes (a dialog, a sheet, an inline editor), and must its work stop when it closes? | **Slot** |
| Is a part drawn on several screens, or once per list item, and does it do its own IO? | **Component ViewModel** |
| Do two screens need the same logic, or does it coordinate several repositories? | **Use case** |
| Does a live source matter only while the screen is visible (a live count, a location)? | **`observeWhileSubscribed`** |
| Would the user be upset to lose what they typed when the app is killed in the background? | **Keeper** |

Levels are not a ranking. A level 1 screen is not unfinished, and a level 3 screen is not better. Each level
costs more code than the one below, and it pays for itself only past its line.

## 4. The rules

Each rule has a reason. A rule broken on purpose needs a comment that gives a reason as good.

### State

1. **One immutable state per store.** It is a `data class`, or at level 3 a sealed type of phases. Do not use
   `var` fields, mutable collections, or a `MutableStateFlow` per field. The state can then be compared in a
   test, saved, logged and diffed. The state is what the *logic* needs to decide the next step. What the
   *screen* draws can be a mapped view of it ([section 11](#11-internal-state)).
2. **Store facts, compute the rest.** `canSend` is a property. A stored copy of it could go stale.
3. **A request's result is a `ContentState`** (`Idle`, `Loading(mock)`, `Success`, `Error`), not three
   fields that can contradict each other.
4. **What a screen must still show after a rotation is state, not an effect.** An error banner is state; a
   one-off snackbar is an effect.

### Intents

5. **Named after what the user did:** `EditQuery`, `Send`, `ToggleFollow`. Not `StartSearch` or
   `CallApi`.
6. **They carry raw input.** Trimming, validating and deciding happen in the logic, never in an `onClick`.

### Effects

7. **Only for things that happen once:** navigation, a message, haptics.
8. **They are the logic's outputs, and someone handles every one.** The route collects them with
   `CollectEffects`. A child's effects go to its parent (see Children).
9. **One collector per store.** A second collector at the same time fails at once, so two screens cannot
   silently share effects.

### Logic

10. **Read the state before you suspend, and write what a guard reads before you launch.** Copy what the
    work needs into locals, or into an action at level 2, before `launch`: after a suspension, `state` may
    already be different. Set the flag that a second tap's guard reads (`isSending`) in the intent's
    synchronous part, not inside the `launch`: `postIntent` returns once that part is done, so the next
    intent sees it however the work is dispatched ([reference](reference.md#threading-and-ordering)).
11. **IO goes through `safeCall { }`,** and each failure ends as a state or an effect. A failure that
    escapes goes to `onError`, which throws unless the app set its own: in development and in tests a bug
    crashes, and an app reports it and keeps running. A failure the user never sees is still a bug.
12. **Dependencies come through the constructor.** A logic must not use Android, Compose, `viewModelScope`
    or a service locator.
13. **Do not keep `Job`s.** A `Job?` field is the signal to move to level 2, where a policy does that work.
    Other private fields in a logic are allowed only in the three cases of [section 11](#11-internal-state).

Intents are posted from the main thread. The store keeps what runs in plain maps, and the synchronous part
of an intent runs on the caller's thread, so a second thread would race it. A ViewModel's scope is
`Dispatchers.Main.immediate`, which is what the tests assume.

### ViewModel

14. **A ViewModel is `ViewModel(), StoreOwner<S, I, E>` with `override val store = store(storeFactory, …)` and nothing
    else.** It has no functions, no other state, and no `init` blocks that do work.

### Screen

15. **Only the route composable touches the `Store`.** Everything below it takes `State<…>` and a
    `postIntent` lambda.
16. **Each component reads its part with `select { }`,** so it recomposes only when its part changes. When a
    component needs a different shape, write a pure mapper (`toBodyUi()`) in the UI layer.

### Children (slices and slots)

17. **Children never reference each other.** When one needs something another has, the parent passes it in
    an intent's parameters.
18. **Each part of the state has one writer,** the child that owns it. The parent writes a child's part only
    to open or close a slot, or to reset everything.
19. **Rules that span children belong to the parent,** for example "nothing is editable while publishing".
20. **Attach children in the parent's `onStart`.**

## 5. Screens that need fewer than three types

The contract stays `Store<S, I, E>`. A channel the screen does not need gets the type `Nothing`. `Nothing`
has no values, so the compiler stops anyone from using that channel: the logic cannot `emit`, and the screen
cannot `postIntent`.

| The screen has | Write | Example |
|---|---|---|
| State and intents, no effects | `Logic<S, I, Nothing>` | A form slice, a toggle: `PollLogic`, `BodyLogic`. No `CollectEffects` on the screen. |
| State only | `Logic<S, Nothing, Nothing>` with `override fun StoreScope<S, Nothing>.onIntent(intent: Nothing) = Unit` | A read-only screen fed by `observe(flow)` in `onStart` |
| Effects only | `Logic<Unit, Nothing, E>`, with the work in `onStart` | A splash that picks a route |
| Nothing at all | No ViewModel | A static page |

A screen with effects and no state is rare. It almost always has a loading or error state you have not
written yet. If it really has none, the work probably belongs to the parent or to a use case, and not to a
screen.

When a missing channel is needed later, replace `Nothing` with the new type. The screen and the tests stay
as they are.

## 6. Keeping the generics quiet

You write the type arguments once per feature, and inference fills them in everywhere else:

- **ViewModel:** `override val store = store(storeFactory, ReportState(), ReportLogic(…))` infers the type. The type
  arguments appear once, in `StoreOwner<…>`.
- **Screen:** only the route takes `Store<S, I, E>`. The components below it take plain values and lambdas.
- **Tests:** `storeTest(initial, logic) { }` infers the types.
- **Logic:** the receiver `StoreScope<S, E>` repeats on every handler. Give each logic class a nested alias,
  the first line of its body: `private typealias Scope = StoreScope<ReportState, ReportEffect>`, then
  `override fun Scope.onIntent(…)` and `private fun Scope.send()`. It is nested, so one file can hold several
  logics. The alias cannot be written in a generic base class (`ActionLogic<S, I, A, E>`), because a nested
  alias does not see the outer type parameters. Only the concrete logic names its types.
- **A slot whose `onEffect` closes the slot itself** needs its type written out, because a property that
  refers to itself cannot be inferred (see `FeedLogic`). This is a known cost.

Never hide the types behind `Any`, star projections or casts. The type checks are what make a move between
levels safe.

## 7. Testing

Every logic gets a test in `commonTest`, written with `storeTest` from `core/testing`:

```kotlin
storeTest(initial, logic) {
    send(intent)                         // as the screen would
    expectState { copy(field = value) }  // each change, as a change from the state before
    expectEffect(effect)
    advance(1_000)                       // virtual time: delays, debounces, timeouts
    subscribe()                          // a visible screen, for whileSubscribed work
}
```

- **Test behaviour through intents,** not private functions. The intents, states and effects are the
  contract.
- **The test fails if anything is left over:** a state change, an effect, or an error the test did not
  expect.
- **Pure pieces get plain unit tests:** gatherers (`checkDraft()`), transitions (`answered(result)`), named
  partials and mappers.
- **What to cover at each level:**

  | Level | What to cover |
  |---|---|
  | 1 | The happy path, each failure path, and each guard (what is ignored and when) |
  | 2 | Each policy: a fast second intent cancels, is dropped, queues, or runs alongside, as declared |
  | 3 | Each transition, and that an intent in the wrong phase does nothing |
  | 4 | Each slice alone, plus the parent's routing, its cross-slice rules and its handling of child outputs |

- **Before moving a screen between levels, test it through its store.** Assert only on what the screen sees
  and on effects, so the same test passes before and after the move. `SignInBehaviourTest` runs one suite
  against four implementations.

## 8. Names and files

One file per concern, named after the main type in it. A feature is a folder, and a folder is a package.

```
report/                        a screen
  ReportContract.kt            State, Intent, Effect (level 2 adds Action and Partial: SearchContract.kt)
  ReportLogic.kt               the logic
  ReportViewModel.kt           the ViewModel, one line
  ReportScreen.kt              route + stateless content
  ReportMappers.kt             only when the UI needs shapes of its own (toBodyUi())
createpost/                    a screen made of slices
  CreatePostContract.kt  CreatePostLogic.kt  CreatePostViewModel.kt  CreatePostScreen.kt
  CreatePostKeeper.kt          what survives process death: the saved part and the keeper
  body/  BodyContract.kt  BodyLogic.kt        a slice: a subpackage, with its own contract and logic
  publish/  PublishContract.kt  PublishLogic.kt
feed/                          a screen with a component
  like/  LikeContract.kt  LikeLogic.kt  LikeViewModel.kt   a component ViewModel: its own subpackage
data/                          models, and one file per repository and per fake
```

| Thing | Name | File |
|---|---|---|
| State, Intent, Effect | `ReportState`, `ReportIntent`, `ReportEffect` | `ReportContract.kt` |
| Actions, partials (level 2) | `SearchAction`, `SearchPartial` | `SearchContract.kt` |
| Phases and events (level 3) | `SignInPhase.Editing`, `SignInEvent` | `SignInPhase.kt`, `SignInEvent.kt` |
| Pure transitions (level 3) | `SignInPhase.Editing.submitted()` | `SignInLevel3Transitions.kt` |
| Logic | `ReportLogic` | `ReportLogic.kt` |
| ViewModel | `ReportViewModel` | `ReportViewModel.kt` |
| Screen | `ReportScreen` (route) and `ReportContent` (stateless) | `ReportScreen.kt` |
| Mappers to component state | `toBodyUi()` | `CreatePostMappers.kt` |
| A mapper from the logic's state to the screen's (`mapState`) | `SignInLevel1State.toUi()` | `SignInLevel1Mapper.kt` |
| Slices | a subpackage each: `createpost/body/`, `createpost/poll/` | `BodyContract.kt`, `BodyLogic.kt` |
| Tests | `ReportLogicTest` | `commonTest`, the same package and folder |

A contract and a logic that share a file are allowed only in a slice that is a few lines, and they split
the day either one grows. A ViewModel never shares a file with the logic: that is what keeps it one line.

Name intents after what the user did (`EditQuery`), actions after what to do (`Search`), partials after the
change (`Loading`, `Results`), and phases after the state the screen is in (`Verifying`).

## 9. Review checklist

For every pull request that adds or changes a screen:

- [ ] The screen is at the lowest level whose line it has crossed (see [migrations](migrations.md)). A level
      above that has a comment saying why.
- [ ] The state is immutable, and nothing stored can be computed from other fields.
- [ ] Intents say what the user did and carry raw input.
- [ ] Effects are one-off only, and every effect is handled.
- [ ] The logic reads the state into locals or an action before suspending.
- [ ] IO is wrapped in `safeCall`, and each failure ends as a state or an effect.
- [ ] The logic has no `Job` fields, no `if (isLoading) return` guards against overlap, and no "is this
      result still current" checks. Any of these means level 2.
- [ ] No three flags exclude each other, and no handler starts with a guard that reads two flags. Either
      means level 3.
- [ ] A logic that has a private `var` or a mutable collection falls into one of the three cases of
      [section 11](#11-internal-state), and says which in a comment.
- [ ] If the logic's state is not what the screen draws, the ViewModel maps it with `mapState`.
- [ ] The ViewModel is a `StoreOwner` with one line.
- [ ] Only the route composable takes the `Store`.
- [ ] Children do not reference each other, and each part of the state has one writer.
- [ ] The logic has a `storeTest`, and pure pieces have unit tests.

## 10. Common mistakes

| Mistake | Why it hurts | Instead |
|---|---|---|
| `onClick = { vm.store.postIntent(Submit(email.trim())) }` | The screen decides behaviour, and that rule is not tested | `postIntent(Submit)`. The logic trims |
| A navigation flag in the state (`val navigateHome: Boolean`) | It fires again after a rotation, or never resets | An effect |
| An error message as an effect when the screen must keep showing it | It is gone after a rotation | State |
| `launch { val q = state.query; … }` | `state` is read after the intent, maybe after other changes | `val q = state.query` before `launch`, or an action |
| A `Job?` field to cancel the previous search | Hand-made concurrency that every new path must remember | Level 2, `Policy.Latest("search")` |
| `isLoading`, `isVerifying`, `isLocked`, `step` side by side | Impossible combinations can be written, and every rule touches every guard | Level 3 phases |
| A function on the ViewModel (`fun refresh()`) | The behaviour is split between the ViewModel and the logic, and untested | An intent |
| One slice calling another (`tags.postIntent(…)` from the poll slice) | A hidden coupling: the split exists in name only | The parent routes |
| A parent writing into a child's state | Two writers for one part of the state, so changes race | Send the child an intent |
| A `StateHolder` with mutable fields | No single transition point, so no logs, no replay, no value to compare | One immutable state and mappers |
| A `private var lastQuery` in the logic that the screen also needs | State that is read by the screen but hidden from the store: it cannot be tested, kept or logged | Put it in the state |
| A `private var` that changes what the logic does next (`var retries = 0`) | Behaviour the state does not show, lost on process death, invisible in a test | Put it in the state, or in the action |
| A `mutableMapOf` cache that is never reset | It outlives the data it describes | Keep the cache in the repository, which owns data lifetime |
| Moving up a level "to be ready" | Code that pays for a problem the screen does not have | Wait for the line |

## 11. Internal state

"What does the logic remember?" has five different answers. Each has one home. Choosing the wrong home is the
source of most hidden-state bugs.

| What it is | Example | Home |
|---|---|---|
| **A fact the screen draws** | the query, the list, `isSending` | The state |
| **A fact only the logic needs, and that survives rotation** | a phase's attempt number, a request's page cursor, the id of the draft being edited | **The state**, hidden from the screen with `mapState` |
| **A fact that is derived** | `canSend`, `isEmpty` | A property of the state |
| **Bookkeeping about work, not about data** | which actions run, which phase's work is running | The runtime (`ActionLogic`, `whileIn`). You never write it |
| **A resource, not a value** | an open socket, a `Job`, a cache of decoded images | The data layer (repository, use case), not the logic |

The rule behind the table: **if the logic's next decision depends on it, it is state.** A private field that
affects behaviour is state the store cannot see. It is not in a test's `expectState`, not in the logs, not in
a keeper, and it is gone when the logic is recreated. Move it into the state.

### 11.1 Hidden from the screen: `mapState`

The state can hold more than the screen draws. `mapState` gives the screen a `Store<Ui, I, E>`:

```kotlin
data class CheckoutState(
    val items: List<CartItem> = emptyList(),
    val cursor: String? = null,            // internal: the next page
    val attempt: Int = 0,                  // internal: retries of the payment
    val isPaying: Boolean = false,
)

data class CheckoutUi(val rows: List<RowUi>, val total: String, val canPay: Boolean)

fun CheckoutState.toUi() = CheckoutUi(
    rows = items.map { it.toRowUi() },
    total = items.sumOf { it.price }.format(),
    canPay = items.isNotEmpty() && !isPaying,
)

class CheckoutViewModel(…) : ViewModel(), StoreOwner<CheckoutUi, CheckoutIntent, CheckoutEffect> {
    override val store = store(storeFactory, CheckoutState(), CheckoutLogic(…)).mapState { it.toUi() }
}
```

Use it when:

- the state has fields the screen never reads (cursors, attempts, ids, timestamps);
- the state is a sealed type of phases and the screen wants one flat shape (level 3, as in `SignInUi`);
- you want to change the logic's state without changing the screen (the sign-in example in [migrations](migrations.md#the-worked-example-sign-in-at-every-level) does exactly
  that at three levels).

Do not use it when the state is already what the screen draws. A mapper that copies every field adds a type
and no value.

Costs and rules:

- `transform` runs on every read, and it is not cached. It must be **pure and cheap**. A mapping that sorts
  or formats a long list belongs in the logic (store the result in the state), or inside a `select { }` in
  the UI that recomposes only when its part changes.
- The ViewModel then declares `StoreOwner<Ui, I, E>`. The logic still has `Logic<State, I, E>`.
- Per-component mapping stays in the UI with `select { }` and mappers like `toBodyUi()`. `mapState` is for
  the whole screen, once, at the ViewModel.

### 11.2 Testing a mapped store

Two layers, two kinds of test:

| Test | Through | Asserts |
|---|---|---|
| The logic's behaviour | `storeTest(initial, logic)`, the internal state | Every change of the state, including the internal fields |
| What the screen sees | `testStoreFactory().create(scope, initial, logic).mapState(toUi)` | `toUi` of each state: rows, `canPay` |
| The mapper | A plain unit test | `CheckoutState(…).toUi() == CheckoutUi(…)` |

When a behaviour test must pass for several implementations of one screen, it goes through the mapped store
(see `SignInBehaviourTest`). The mapper itself is tested once, as a pure function.

### 11.3 The three cases where a private field is allowed

1. **A constant or a dependency:** `private val posts: PostRepository`, `private val debounce = 300L`. It
   never changes.
2. **A host-owned helper** that the runtime already requires: a `Slice` or a `Slot` field,
   attached in `onStart`. A logic instance belongs to one host, so this bookkeeping lives with it.
3. **A cache that is only an optimisation,** where losing it changes speed and not behaviour (a compiled
   `Regex`, a formatter). If losing it changes what the user sees, it is state.

Anything else, say `private var page = 0`, fails the rule above (its next decision depends on it). Put it in the state, and `mapState` it away
if the screen does not draw it.

### 11.4 Internal state and process death

A keeper saves a *part* and merges it back into the initial state (see [reference](reference.md#surviving-process-death)).
The internal fields follow the same rule as the rest:

| Field | Keep? |
|---|---|
| What the user typed or chose (a draft, a step, a selection) | Yes |
| A cursor, an attempt count, a token, a loaded list | No: it is fetched or started again |
| A phase such as `Submitting` | Never: the work behind it is gone |

That is a second reason to keep internal fields in the state and not in a `private var`: a keeper can decide
about a field of the state, and cannot see a `private var`.

### 11.5 Where each thing goes, in one page

| I need to… | Do this |
|---|---|
| Remember what the last request was, to ignore a stale answer | Level 2, `Policy.Latest(key)`. No field |
| Know the next page | `cursor` in the state, `mapState` away |
| Count retries | `attempt` in the state or in the action |
| Not run the same work twice at once | A `Policy` (`Ignore` or `Latest`) |
| Share a loaded list between screens | A repository that exposes a `Flow`; `observe` it in `onStart` |
| Cache decoded or parsed data | The repository or a use case |
| Run work only while the screen is on | `observeWhileSubscribed` / `whileSubscribed` |
| Hold something for the screen's whole life that is not data | It is a dependency: the constructor |

## 12. Dependency injection and the size of a ViewModel

The architecture needs no DI framework, and works with any. The reason is rule 12: a logic gets everything
through its constructor and knows nothing of Android, Compose or a service locator. A DI framework then
sees an ordinary class. Dagger/Hilt (Android only), Metro, kotlin-inject and Koin all build it, and a test
builds it by hand with fakes.

### 12.1 Four rules

1. **A logic is unscoped.** Every injection gives a new instance. A logic belongs to one host (a ViewModel,
   a slice, a slot) because it may keep that host's bookkeeping, so `@Singleton` or any shared scope on a
   logic is a bug. Shared things (repositories, sessions) are scoped; logics are not.
2. **Runtime arguments are assisted.** `ReportLogic(postId, posts)` takes `posts` from the graph and `postId`
   from the caller. The logic declares a nested factory, and a DI framework implements it:

   ```kotlin
   class ReportLogic(private val postId: String, private val posts: PostRepository) : Logic<…> {
       fun interface Factory { fun create(postId: String): ReportLogic }
   }
   ```

   With Metro this is `@AssistedInject` on the class, `@Assisted` on `postId`, and `@AssistedFactory` on
   `Factory`. Metro matches assisted parameters by name. Dagger and Hilt have the same three annotations.
   Without a framework, `ReportLogic.Factory { id -> ReportLogic(id, posts) }` is the whole factory.
3. **A parent gets its children's logics from the constructor, and never builds them.** `CreatePostLogic`
   takes `BodyLogic`, `PollLogic`, `TagsLogic` and the publish logic. `FeedLogic` takes a
   `ReportLogic.Factory` for its sheet, since each opening needs a new logic for another post. A parent that
   writes `TagsLogic(posts)` inside itself cannot be given a different `TagsLogic`, and needs `posts` for
   nothing it does itself. Beside such a logic, a function of the same name builds the usual set for
   screens made by hand and for tests (`CreatePostLogic(posts)`, `FeedLogic(posts)`).
4. **The logic never sees the DI framework.** No `@Inject`-only types, no `Provider<T>` in its logic code, no
   graph. If it needs something late or repeatedly, it takes a function type or a factory (`() -> T`).

### 12.2 What the ViewModel takes

There are two shapes. Both obey rule 14 (a ViewModel has one line of body):

| Shape | ViewModel | When |
|---|---|---|
| **Takes its dependencies** | `ReportViewModel(storeFactory, postId, posts)` builds `ReportLogic(postId, posts)` | No DI, or a small screen |
| **Takes the logic** | `ReportViewModel(storeFactory, logic: ReportLogic)`, then `store(storeFactory, ReportState(), logic)` | With DI. The framework builds the logic, so the ViewModel never learns its dependencies |

The second is the thin one, and it is what to use with a framework:

```kotlin
class ReportViewModel(
    storeFactory: StoreFactory,
    logic: ReportLogic,
) : ViewModel(), StoreOwner<ReportState, ReportIntent, ReportEffect> {
    override val store = store(storeFactory, ReportState(), logic)
}
```

Whether the ViewModel itself is injected, and how a Compose screen asks for it, is the framework's own
ViewModel integration (Hilt's `hiltViewModel()`, Metro's `metrox-viewmodel`). That part is not specific to
this architecture, so check the framework's current documentation for its exact setup.

### 12.3 The biggest ViewModel there can be

A ViewModel never holds behaviour, so its size is only what it needs to *build* the store. The most it can
ever have is five things:

| Part | Where it comes from |
|---|---|
| 1. The logic | injected (or built from injected dependencies) |
| 2. The initial state | a default (`ReportState()`), or built from an argument (`LikeState(post.isLiked, post.likes)`) |
| 3. A keeper | built from the `SavedStateHandle` |
| 4. `StoreFactory` | injected once for the whole app, with its `StoreSettings` |
| 5. `mapState` | when the logic's state is not what the screen draws ([section 11](#11-internal-state)) |

All five at once, written out:

```kotlin
class CheckoutViewModel(
    logic: CheckoutLogic,                  // 1
    savedState: SavedStateHandle,          // 3
    storeFactory: StoreFactory,            // 4
) : ViewModel(), StoreOwner<CheckoutUi, CheckoutIntent, CheckoutEffect> {
    override val store = store(
        factory = storeFactory,            // 4
        initialState = CheckoutState(),    // 2
        logic = logic,
        keeper = savedState.draftKeeper(), // 3
    ).mapState { it.toUi() }               // 5
}
```

That is the ceiling: one expression, with no function, no `init`, and no second field. A draft screen shows parts 1 to 3.

`StoreFactory` is what carries the app's `StoreSettings` — how a failure that escapes a logic is reported, which
observers see every intent and state — so no ViewModel names them, and a base class has nothing left to hold.
`StoreOwner` is an interface, so an app's own base ViewModel works as it is.

If a ViewModel has grown past this (a function, a second `StateFlow`, a `launch`, an `init` that does work),
that code is behaviour. Move it into the logic as an intent, an `onStart`, or a use case. It never stays in
the ViewModel.

### 12.4 Components per list item

A component ViewModel (`LikeViewModel(post, posts)`) is built per item and keyed by the item's id. Its
runtime argument (`post`) is assisted in exactly the same way: the item's composable asks the framework for
the ViewModel with an assisted factory, and the item's id is the key. The logic inside is `LikeLogic(postId, posts)`,
which a test builds without the ViewModel.

### 12.5 What to check in review

- [ ] No `@Singleton` (or other shared scope) on a logic.
- [ ] A runtime argument is assisted, never read from a global or a service locator.
- [ ] A parent logic does not write `ChildLogic(...)`: the constructor takes it, or takes a factory.
- [ ] No DI types appear in a logic's code.
- [ ] A ViewModel has one expression: `store(...)`, plus at most `mapState`.
