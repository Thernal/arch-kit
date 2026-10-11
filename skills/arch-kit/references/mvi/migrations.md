# Migrations

When a screen moves to another level or another host, and how to make the move. Moves go up and down: a
screen goes up when it crosses a line, and comes back down when it crosses that line back and stays there.

In the code on this page, `Scope` is the logic class's `private typealias Scope = StoreScope<State, Effect>`
([guide, section 6](guide.md#6-keeping-the-generics-quiet)).

Read the [guide](guide.md) first. This page assumes you know the words used there: level, action, policy,
phase, slice, slot.

Contents:

- [How every move works](#how-every-move-works)
- [The map](#the-map)
- [Level 1 ⇄ level 2: overlapping work](#level-1--level-2-overlapping-work)
- [Level 1 or 2 ⇄ level 3: exclusive modes](#level-1-or-2--level-3-exclusive-modes)
- [Any level ⇄ level 4: slices](#any-level--level-4-slices)
- [Slice ⇄ slot](#slice--slot)
- [Part of a screen ⇄ component ViewModel](#part-of-a-screen--component-viewmodel)
- [Logic ⇄ use case](#logic--use-case)
- [Always on ⇄ only while visible](#always-on--only-while-visible)
- [No keeper ⇄ keeper](#no-keeper--keeper)
- [The worked example: sign-in at every level](#the-worked-example-sign-in-at-every-level)

## How every move works

1. **The outside stays the same.** The screen sees `Store<State, Intent, Effect>`, and a move keeps all
   three types. When the state's shape has to change (flags become phases), the ViewModel maps the new
   state onto the old one with `mapState { it.toUi() }`, so the screen does not change. The only part that
   changes is the logic, and sometimes the ViewModel's one line.
2. **Lock the behaviour first.** Before you touch the logic, write `storeTest`s, or a behaviour suite like
   `SignInBehaviourTest`, that send intents and assert only on the state the screen sees and on effects.
   They must pass before the move and after it, without changes. A test that has to change during a move is
   testing the implementation, not the behaviour.
3. **One move per pull request.** Do not combine a move with a feature. A reviewer should be able to read the
   diff as "same behaviour, different structure".
4. **The lines are measurable.** Each line below is something you can point to in a diff: a `Job?` field, a
   guard, a line count. "It feels complex" is not a line.
5. **Moving down needs the line to be crossed back for good.** Go down when a feature has been removed or
   redesigned away, and the roadmap does not bring it back. Do not go down because a feature is behind a
   flag or disabled for one release. Moving a screen down and back up costs two migrations and buys nothing.
   If a screen sits exactly on a line, it stays where it is.

## The map

```
                 work overlaps                    modes exclude each other
   ┌─────────┐ ──────────────▶ ┌─────────┐ ─────────────────────────▶ ┌─────────┐
   │ Level 1 │                 │ Level 2 │                            │ Level 3 │
   │ direct  │ ◀────────────── │ actions │ ◀───────────────────────── │ phases  │
   └─────────┘ no overlap left └─────────┘ one mode or two left       └─────────┘
        │  ▲                                                               ▲
        │  └──────────── modes exclude each other, no overlap ─────────────┘
        │
        └─ at any level: big, or intents in independent groups ──▶ Level 4: slices, each at its own level

   Hosts, at any level:  slice ⇄ slot  ·  slice ⇄ component ViewModel  ·  logic ⇄ use case
```

| Move | Go when | Come back when | What changes | What stays |
|---|---|---|---|---|
| 1 → 2 | A second request can start while the first runs | Only one kind of work is left, and it cannot overlap | `Logic` → `ActionLogic`, plus an action type | State, intents, effects, screen |
| 1/2 → 3 | Three or more flags exclude each other, or a handler guards on two flags | One or two modes are left | State → sealed phases, plus `toUi()` and `mapState` | Intents, effects, screen |
| → 4 | About 300 lines, or intent groups that never touch each other's state | One slice or two small ones are left, under about 150 lines | Parent → composition root, parts → child logics | Screen, apart from the intent wrapping |
| slice ⇄ slot | The part exists only sometimes, and its work must stop when it closes | The part is always shown | Child state becomes nullable, `Slice` → `Slot` | The child logic |
| part ⇄ component VM | The part repeats per item or appears on several screens, and owns its IO | The parent must coordinate it: reset it, or read its state for its rules | The host | The child logic |
| logic ⇄ use case | Two logics need it, or it coordinates several repositories | It only forwards one repository call | Where the code lives | Behaviour |
| `observe` ⇄ `observeWhileSubscribed` | The source matters only while the screen is visible | It must stay current off screen | One call | Everything else |
| keeper | Losing the input would upset the user | The state is only loaded data | The ViewModel's line, a saved type | The logic |

## Level 1 ⇄ level 2: overlapping work

### Signals to go up

Any one of these in a level 1 logic:

```kotlin
private var searchJob: Job? = null                         // a job kept by hand
if (state.isLoading) return                                 // a guard against a double tap
if (state.query == query) setState { copy(results = r) }   // "is this result still current?"
private val followJobs = mutableMapOf<String, Job>()        // a job per row
```

Each one is a concurrency rule written by hand, and every new path must remember it. Level 2 names the rule
once, as a `Policy`.

### Steps

1. **Write the actions.** Create a sealed `XAction`, with one class per kind of work. Each action carries
   every parameter its work needs, so the work never reads `state`.
2. **Change the base class.** `Logic<S, I, E>` becomes `ActionLogic<S, I, XAction, E>()`, and `onIntent`
   becomes `toAction(intent): XAction?`. In `toAction`:
   - reduce the synchronous changes there, such as a typed character;
   - read the state and build the action, or return `null` when there is nothing to run.
3. **Move each `launch { }` body into `process(action)`.** Replace every `state.x` inside it with the
   action's parameter.
4. **Give each action a policy, which carries its key.** There is no default key: one shared by a class of
   actions would make "follow B" cancel "follow A" without a sign.

   | Work | Policy |
   |---|---|
   | Search as you type, filters | `Policy.Latest("search")` |
   | Submit, pay, pull to refresh | `Policy.Ignore("submit")` |
   | A toggle per row (follow, like) | `Policy.Latest("follow:$id")`, one key per row |
   | Writes that build on each other (reorder, append) | `Policy.Queue("order")` |
   | Independent work | `Policy.Parallel` |

5. **Delete what the policies replaced:** the `Job` fields, the overlap guards, and the staleness checks.
6. **Optionally, name the changes** as a sealed `XPartial : Partial<S>`, so logs and tests show
   `SearchPartial.Results` and not a lambda.
7. **Add a test per policy.** For example, two quick searches leave only the second result, and a double
   submit sends one request.

```kotlin
// Before (level 1)
is SearchIntent.EditQuery -> {
    setState { copy(query = intent.text) }
    searchJob?.cancel()
    searchJob = launch {
        delay(300)
        val result = safeCall { users.search(intent.text.trim()) }
        if (state.query.trim() == intent.text.trim()) setState { copy(results = result.toContentState()) }
    }
}

// After (level 2)
override fun Scope.toAction(intent: SearchIntent) = when (intent) {
    is SearchIntent.EditQuery -> {
        reduce(SearchPartial.Query(intent.text))
        SearchAction.Search(query = intent.text.trim(), debounce = true)
    }
    // …
}
override fun policy(action: SearchAction) = Policy.Latest("search")   // the next keystroke cancels this one
override suspend fun Scope.process(action: SearchAction) {
    /* delay, then IO, then reduce */
}
```

### Signals to come back down

Only one kind of work is left, and the user cannot start it twice: a single send whose button is disabled
while it runs. An `Ignore` policy guarding one button is fine at level 2, but at level 1 the same rule is one
honest `if (state.isSending) return`, as in `ReportLogic`.

**Going down:** fold `toAction` and `process` back into `onIntent` with a `launch`. Gather the parameters
into locals before the `launch`, then delete the action type.

## Level 1 or 2 ⇄ level 3: exclusive modes

### Signals to go up

- Three or more flags that exclude each other: `isSubmitting`, `isVerifying`, `isLocked`, `step`.
- A handler that starts with a guard reading two flags: `if (step != Code || isVerifying) return`.
- A bug report like "the countdown kept running after going back", or "two spinners at once". Both come from
  a combination of flags that should be impossible.

At level 2 the jobs are gone, but the flags and guards stay. That is exactly where sign-in level 2 ends up.

### Steps

1. **List the modes.** Ask, for every moment of the screen, "what is it doing now?". Each answer is a phase.
   Each phase holds only the data valid in it. For example, `Verifying` has a `challengeId`, and `Editing`
   does not.

   ```kotlin
   sealed interface SignInPhase {
       data class Editing(val email: String = "", val password: String = "", val failedAttempts: Int = 0, val error: String? = null) : SignInPhase
       data class Submitting(val email: String, val password: String, val failedAttempts: Int) : SignInPhase
       data class Locked(val email: String, val password: String, val secondsLeft: Int) : SignInPhase
       data class EnteringCode(val challengeId: String, val code: String = "", /* … */) : SignInPhase
       data class Verifying(val challengeId: String, val code: String, /* … */) : SignInPhase
       data object SignedIn : SignInPhase
   }
   ```

   Phases that share data can share a sealed parent. In the sign-in example below, `EnteringCode` and `Verifying` are both
   `Code`, so one countdown runs across both.

2. **Write the transitions as pure functions** on phases, such as `Editing.submitted()` and
   `Submitting.answered(result)`, and unit test them. They have no coroutines in them, so they are
   tested without a store.
3. **Handle intents per phase:** `when (val phase = state) { is Editing -> when (intent) { … } … }`. A phase
   that does not list an intent ignores it. This replaces every guard.
4. **Bind the work to phases** in `onStart`:
   - `whileIn(Submitting::class) { submitting -> … }` starts when the phase is entered and is cancelled when
     it is left;
   - `runOn(Code::class) { … }` is work started by an intent that dies when the phase is left;
   - `setState { answered(result) }` inside them is ignored if the phase was already left, so a late result
     cannot overwrite where the user went.
5. **Keep the screen unchanged.** Write `SignInPhase.toUi(): SignInUi` and host the store as
   `store(storeFactory, initial, logic).mapState { it.toUi() }`.
6. **Delete the flags, the guards and the jobs.**
7. **Test each transition, and test that an intent in the wrong phase does nothing.**

### Signals to come back down

One or two modes are left. For example, `Editing` and `Submitting` only: that is a single `isSubmitting`
flag, which is level 1. Fold the phases back into one data class and delete `toUi()` if the screen's state
is the logic's state again.

## Any level ⇄ level 4: slices

### Signals to go up

- The logic is past about 300 lines.
- Its intents fall into groups that never touch each other's state. In create-post, the body, poll, tags and
  publishing groups each touch only their own fields.
- Two developers keep conflicting in the same logic file.

### Steps

1. **Group the intents** by the state they touch. Each group with its own state is a slice. An intent that
   reads several groups stays in the parent.
2. **Give each slice its own state,** nested in the parent's: `val body: BodyState = BodyState()`.
3. **Write each slice's logic at its own level,** with its own intents, and its own effects (`Nothing` if it
   has none). Body and poll are level 1, tags is level 2, and publishing is level 3.
4. **Turn the parent into a composition root.** It owns only:
   - routing: `is CreatePostIntent.Body -> body.postIntent(intent.intent)`;
   - rules that span slices: `if (editable)`;
   - gathering across slices: `state.checkDraft()` builds what the publish slice is sent;
   - the children's outputs: `Slice(…, onEffect = { effect -> … })`, or `slice(…)` to forward effects as
     they are.
5. **Attach the slices** in the parent's `onStart`.
6. **Intents.** The screen posts `CreatePostIntent.Body(BodyIntent.Edit(text))`. To keep the screen
   unchanged during the move, keep the old flat intents for now and wrap them in the parent's routing, then
   switch the screen in a follow-up.
7. **Test each slice alone** with `storeTest`, and test the parent's routing, rules and output handling.

### Signals to come back down

Features left and one slice remains, or a few small ones under about 150 lines in total. Move each child's
`when` branches back into the parent's `onIntent`. Its nested state can stay nested or be flattened. Keep the
pure gatherers.

## Slice ⇄ slot

**Go to a slot** when a part exists only sometimes (a dialog, a bottom sheet, an inline editor), and its
work must stop when it closes. With a slice, a report still being sent would keep running after the sheet is
dismissed.

1. Make the child's state nullable in the parent: `val report: ReportState? = null`. `null` means closed.
2. Replace the `Slice` with `Slot(get, set, create = { config -> Hosted(initial, Logic(config)) }, onEffect)`.
3. Open and close it from the parent's intents: `report.open(postId)` and `report.close()`.
4. Treat the child's "done" output as the cue to close it: `ReportEffect.Sent -> { report.close(); emit(…) }`.
5. The screen shows the sheet while `state.report != null`.

**Back to a slice** when the part is always shown: make the state non-null and replace `Slot` with `Slice`.

The child logic does not change in either direction. The feed opens the report screen's own logic, unchanged,
as a sheet.

## Part of a screen ⇄ component ViewModel

**Go to a component ViewModel** when a part is drawn once per list item (a like button per card), or on
several screens, and does its own IO.

1. Keep the logic as it is. Add `class LikeViewModel(…) : ViewModel(), StoreOwner<…> { override val store =
   store(…) }`.
2. In a list, give each item its own ViewModel store: `val cards = rememberItemViewModels(keys)`, then
   `ItemScope(cards, key) { val like = viewModel { LikeViewModel(post, posts) }.store }`. An item's
   ViewModel lives while it is on screen, and after it has scrolled away for as long as its work runs
   (`Store.isBusy`); [case 16](cookbook.md#16-one-piece-per-list-item) has the rules.
3. Remove the part from the parent's state. The parent passes only the initial values.

The trade-off is that the parent can no longer reset the part or read its state. That is why the feed passes
only the initial like count.

**Back to a slice** when the parent needs exactly that: it must reset the part, or use its state in a rule
that spans parts.

## Logic ⇄ use case

**Extract a use case** when two logics need the same code, or when code coordinates several repositories
(upload media, then create a post, then update the cache). The use case holds no presentation types.

**Inline it again** when it only forwards one repository call. A use case like that is not written in the
first place.

## Always on ⇄ only while visible

| Use | When the source |
|---|---|
| `observe(flow) { }` | Must stay current even off screen: the session, settings |
| `observeWhileSubscribed(flow) { }`, `whileSubscribed { }` | Matters only on screen: a live count, a location, a socket |

The move is one call. `whileSubscribed` work stops a timeout (5 s by default) after the screen stops
collecting, and starts again when the screen comes back. Test it with `subscribe()` in `storeTest`.

## No keeper ⇄ keeper

**Add a keeper** when losing the state after the process is killed in the background would upset the user:
a long draft, a multi-step form, a selection.

1. Write a `@Serializable` type for the part to keep. That is the user's input only: not loaded data, not a
   busy phase.
2. Write `StoreFactory.xKeeper(handle) = keeper(handle, key, serializer, save = { … }, restore = { … })`.
3. Pass it in the ViewModel: `override val store = store(storeFactory, initial, logic, keeper = storeFactory.xKeeper(savedState))`.
4. Test the round trip with `storeTest(…, keeper = testStoreFactory().xKeeper(handle))`.

**Remove it** when the state becomes only loaded data, which is fetched again anyway. A saved value that no
longer decodes after an app update is dropped, so changing the saved type is safe but loses the old value.

## The worked example: sign-in at every level

In the cmplayground repository (branch `feat/mvi-patterns-v3`), `samples/signin/` is one screen written three ways: the same intents, the same `SignInUi`, and the same
screen. `SignInBehaviourTest` runs seven behaviours against all three implementations.

- **Level 1** (`SignInLevel1Logic`) works, and shows the cost:
  - five flags, mostly in impossible combinations;
  - a guard at the top of every handler;
  - two `Job`s kept by hand so that two countdowns never tick at once.
- **Level 2** (`SignInLevel2Logic`) keeps the same state and moves the concurrency into policies. The jobs
  are gone, but the flags and guards stay. That is the level 3 signal.
- **Level 3** has the phases `Editing`, `Submitting`, `Locked`, `EnteringCode`, `Verifying` and `SignedIn`.
  It has no flags and no guards, and the countdowns are work of their phases (`whileIn` and `runOn`),
  over transitions that are pure functions.

Each level is a folder of its own with the same four kinds of file, and nothing is shared between levels
except the contract and the screen:

```
signin/
  SignInContract.kt  SignInRules.kt  SignInScreen.kt     shared: intents, effects, SignInUi, the screen
  level1/  SignInLevel1State.kt  SignInLevel1Mapper.kt  SignInLevel1Logic.kt  SignInLevel1ViewModel.kt
  level2/  SignInLevel2Contract.kt  SignInLevel2Logic.kt  SignInLevel2ViewModel.kt
  level3/  SignInPhase.kt  SignInLevel3Transitions.kt  SignInLevel3Mapper.kt
           SignInLevel3Logic.kt  SignInLevel3ViewModel.kt
```

Each implementation maps its state onto `SignInUi` in its ViewModel with `mapState`. The screen cannot tell
which one runs, and that is the property every migration on this page preserves.
