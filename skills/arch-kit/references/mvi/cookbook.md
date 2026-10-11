# MVI cookbook

The cases real screens have, each with one recommended answer. Read the [guide](guide.md) first: this page
assumes its model and its rules. Every case has the same shape: **the situation, the rule, the code, the
test.** The code is short on purpose: it shows the part that makes the case work.

In the snippets, `Scope` is the logic class's own `private typealias Scope = StoreScope<State, Effect>`
([guide, section 6](guide.md#6-keeping-the-generics-quiet)); a snippet that shows only handlers leaves the
alias line out. Imports are left out.

| # | Case | Level |
|---|---|---|
| 1 | [Load on open, with retry](#1-load-on-open-with-retry) | 1 |
| 2 | [Refresh without blanking the screen](#2-refresh-without-blanking-the-screen) | 2 |
| 3 | [Pagination](#3-pagination) | 2 |
| 4 | [Search as you type](#4-search-as-you-type) | 2 |
| 5 | [Optimistic update with rollback](#5-optimistic-update-with-rollback) | 2 |
| 6 | [A form: validation and errors](#6-a-form-validation-and-errors) | 1 |
| 7 | [A multi-step flow](#7-a-multi-step-flow) | 3 |
| 8 | [Observing a source outside the screen](#8-observing-a-source-outside-the-screen) | any |
| 9 | [Arguments in, results out](#9-arguments-in-results-out) | any |
| 10 | [A dialog or sheet with logic](#10-a-dialog-or-sheet-with-logic) | any |
| 11 | [Timers, countdowns, polling](#11-timers-countdowns-polling) | 3 |
| 12 | [Selection mode in a list](#12-selection-mode-in-a-list) | 1 |
| 13 | [Platform calls: permissions, share, haptics](#13-platform-calls-permissions-share-haptics) | any |
| 14 | [Reacting to the lifecycle](#14-reacting-to-the-lifecycle) | any |
| 15 | [Where an error goes](#15-where-an-error-goes) | any |
| 16 | [One piece per list item](#16-one-piece-per-list-item) | any |
| 17 | [Draft that survives the process](#17-draft-that-survives-the-process) | any |
| 18 | [Testing recipes](#18-testing-recipes) | any |
Internal state (cursors, attempts, ids the screen does not draw) is [guide section 11](guide.md#11-internal-state).

## 1. Load on open, with retry

**Situation.** The screen needs data when it opens, and the user can retry after a failure.

**Rule.** The data is one `ContentState`. Load in `onStart`, so a screen that is never shown loads nothing
(`Start.Lazily`). Retry is an intent named after what the user did.

```kotlin
data class ProfileState(
    val profile: ContentState<Profile> = ContentState.Idle,
)

sealed interface ProfileIntent {
    data object Retry : ProfileIntent
}

class ProfileLogic(
    private val userId: String,
    private val profiles: ProfileRepository,
) : Logic<ProfileState, ProfileIntent, Nothing> {
    private typealias Scope = StoreScope<ProfileState, Nothing>

    override fun Scope.onStart() = load()

    override fun Scope.onIntent(intent: ProfileIntent) = when (intent) {
        ProfileIntent.Retry -> load()
    }

    private fun Scope.load() {
        setState { copy(profile = profile.reload()) }
        launch {
            val result = safeCall { profiles.profile(userId) }
            setState { copy(profile = result.toContentState()) }
        }
    }
}
```

Two taps on Retry start two loads. That is the signal for level 2 (case 2).

**Test.**

```kotlin
@Test
fun aFailureCanBeRetried() = runTest {
    storeTest(ProfileState(), ProfileLogic("u1", FakeProfiles(failures = 1))) {
        skip()                                            // Loading, from onStart
        advance(1_000)
        assertIs<ContentState.Error>(nextState().profile)

        send(ProfileIntent.Retry)
        expectState { copy(profile = ContentState.Loading()) }
        advance(1_000)
        expectState { copy(profile = ContentState.Success(Profile("u1", "Ada"))) }
    }
}
```

## 2. Refresh without blanking the screen

**Situation.** Pull to refresh, while the list stays on screen.

**Rule.** `reload()` keeps the previous data in `Loading(mock)`. A refresh is `Policy.Ignore("refresh")`: a second pull
while one runs is ignored. See `FeedLogic`.

```kotlin
class FeedLogic(private val repository: PostRepository) :
    ActionLogic<FeedState, FeedIntent, Unit, FeedEffect>() {
    private typealias Scope = StoreScope<FeedState, FeedEffect>

    override fun Scope.onStart() = dispatch(Unit)

    override fun Scope.toAction(intent: FeedIntent): Unit? = when (intent) {
        FeedIntent.Refresh -> Unit
        // …
    }

    override fun policy(action: Unit) = Policy.Ignore("refresh")

    override suspend fun Scope.process(action: Unit) {
        setState { copy(posts = posts.reload()) }          // the old list stays on screen
        val result = safeCall { repository.feed() }
        setState { copy(posts = result.toContentState()) }
    }
}
```

A failed refresh with data on screen should not replace the list with an error screen. Either:

- keep it simple: `Error` replaces the list, and Retry is shown (right for most screens); or
- keep the list and tell the user once: on failure, put `previous` back with `setState` and `emit` a message.

Choose one per product, and use it on every list.

**Test.** `send(Refresh, Refresh)` makes one request, and the list is never empty while the second answer is
awaited.

## 3. Pagination

**Situation.** A list that loads the next page when the user scrolls near the end.

**Rule.** The items and the cursor are state. The cursor is internal, so it can be hidden with `mapState`
([guide 11.1](guide.md#11-internal-state)). A refresh and a next page share one key under `Policy.Latest("items")`, so
a refresh cancels a page in flight and the reverse. The "loading" state is set in `toAction`, before anything
launches, so two quick `ReachedEnd` intents cannot both pass the check.

```kotlin
data class ItemsState(
    val items: List<Item> = emptyList(),
    val cursor: String? = null,                          // internal: where the next page starts
    val hasMore: Boolean = true,
    val paging: ContentState<Unit> = ContentState.Idle,  // the page loading now
)

sealed interface ItemsIntent {
    data object Refresh : ItemsIntent

    data object ReachedEnd : ItemsIntent
}

sealed interface ItemsAction {
    data object Refresh : ItemsAction

    data class LoadMore(val cursor: String) : ItemsAction   // gathered from the state when the user scrolled
}

class ItemsLogic(private val items: ItemRepository) :
    ActionLogic<ItemsState, ItemsIntent, ItemsAction, Nothing>() {
    private typealias Scope = StoreScope<ItemsState, Nothing>

    override fun Scope.onStart() = dispatch(ItemsAction.Refresh)

    override fun Scope.toAction(intent: ItemsIntent): ItemsAction? {
        return when (intent) {
            ItemsIntent.Refresh -> {
                setState { copy(paging = paging.reload()) }
                ItemsAction.Refresh
            }
            ItemsIntent.ReachedEnd -> {
                val cursor = state.cursor
                if (cursor == null || !state.hasMore || state.paging.isLoading) return null
                setState { copy(paging = paging.reload()) }
                ItemsAction.LoadMore(cursor)
            }
        }
    }

    override fun policy(action: ItemsAction) = Policy.Latest("items")   // a refresh and a page cancel each other

    override suspend fun Scope.process(action: ItemsAction) {
        if (action == ItemsAction.Refresh) setState { copy(paging = paging.reload()) }   // the start's dispatch
        val cursor = (action as? ItemsAction.LoadMore)?.cursor
        safeCall { items.page(cursor) }
            .onSuccess { loaded ->
                setState {
                    copy(
                        items = if (cursor == null) loaded.items else items + loaded.items,
                        cursor = loaded.next,
                        hasMore = loaded.next != null,
                        paging = ContentState.Success(Unit),
                    )
                }
            }.onFailure { error -> setState { copy(paging = ContentState.Error(error)) } }
    }
}
```

- A failed next page keeps `items` and sets `paging = Error`: the list stays, and the footer shows Retry.
- The UI posts `ReachedEnd` from a `LaunchedEffect` that watches the last visible index. The logic decides if
  it matters.

**Test.**

```kotlin
@Test
fun scrollingFastLoadsAPageOnce() = runTest {
    val items = FakeItems()                               // three pages of two, a second each
    storeTest(ItemsState(), ItemsLogic(items)) {
        advance(1_000)                                    // the first page
        skipAll()
        send(ItemsIntent.ReachedEnd, ItemsIntent.ReachedEnd, ItemsIntent.ReachedEnd)
        advance(1_000)
        skipAll()
        assertEquals(2, items.calls)
    }
}
```

## 4. Search as you type

**Situation.** A query changes on every keystroke, and answers return in any order.

**Rule.** `Policy.Latest("search")` with a `delay` at the start of `process`. That is the debounce, and the
cancellation of old answers, with no `Job` field. The typed text is reduced at once in `toAction` (the field
must not lag), and the request is the action. See `SearchLogic`.

```kotlin
override fun Scope.toAction(intent: SearchIntent): SearchAction? = when (intent) {
    is SearchIntent.EditQuery -> {
        reduce(SearchPartial.Query(intent.text))                   // the field must not lag
        SearchAction.Search(query = intent.text.trim(), debounce = true)
    }
    SearchIntent.Retry -> SearchAction.Search(query = state.query.trim(), debounce = false)
    // …
}

override fun policy(action: SearchAction) = when (action) {
    is SearchAction.Search -> Policy.Latest("search")
    is SearchAction.SetFollowing -> Policy.Latest("follow:${action.user.id}")   // one per user
}

override suspend fun Scope.process(action: SearchAction) {
    if (action.query.isEmpty()) return reduce(SearchPartial.Cleared)
    if (action.debounce) delay(300)                                // cancelled by the next keystroke
    reduce(SearchPartial.Loading)
    reduce(SearchPartial.Results(safeCall { users.search(action.query) }))
}
```

**Test.**

```kotlin
@Test
fun onlyTheLastQuerySurvivesTheDebounce() = runTest {
    storeTest(SearchState(), SearchLogic(FakeUserRepository(latency = 1_000))) {
        send(SearchIntent.EditQuery("a"), SearchIntent.EditQuery("ada"))
        expectState { copy(query = "a") }
        expectState { copy(query = "ada") }
        advance(SearchLogic.DEBOUNCE_MILLIS)
        expectState { copy(results = ContentState.Loading()) }    // one search, for "ada"
        advance(1_000)
        expectState { copy(results = ContentState.Success(listOf(User("u0", "Ada Lovelace", false)))) }
    }
}
```

## 5. Optimistic update with rollback

**Situation.** A like, a follow, a toggle: the screen shows the result before the backend agrees.

**Rule.**

1. Gather the **wish** when the user taps (`liked = !state.isLiked`), and send "set X", never "toggle".
2. Change the state at once, in `toAction`.
3. Keep `before` in the action, and put it back on failure, with an effect that says so.
4. Use `Policy.Latest` with a key per thing (`Policy.Latest("follow:$id")`), so rapid taps send the last wish
   only, and different things do not cancel each other. A policy always takes its key: a shared default would
   make "follow B" cancel "follow A" with no sign that anything is wrong.

Do not roll back by recomputing (`!liked`): a second tap may have happened in between. Roll back to the saved
value. See `LikeLogic`.

```kotlin
class LikeLogic(private val postId: String, private val posts: PostRepository) :
    ActionLogic<LikeState, LikeIntent, LikeLogic.SetLiked, LikeEffect>() {
    private typealias Scope = StoreScope<LikeState, LikeEffect>

    data class SetLiked(val liked: Boolean, val before: LikeState)

    override fun Scope.toAction(intent: LikeIntent): SetLiked {
        val before = state
        val liked = !before.isLiked                               // the wish, gathered at the tap
        setState { copy(isLiked = liked, count = count + if (liked) 1 else -1) }   // shown at once
        return SetLiked(liked = liked, before = before)
    }

    override fun policy(action: SetLiked) = Policy.Latest("like")

    override suspend fun Scope.process(action: SetLiked) {
        safeCall { posts.setLiked(postId, action.liked) }
            .onSuccess { count -> setState { copy(count = count) } }
            .onFailure {
                setState { action.before }                        // back to what it was
                emit(LikeEffect.Failed)
            }
    }
}
```

**Test.**

```kotlin
@Test
fun aFailureRollsBack() = runTest {
    storeTest(LikeState(isLiked = false, count = 7), LikeLogic(FakePostRepository.FAILING_POST, posts)) {
        send(LikeIntent.Toggle)
        expectState { copy(isLiked = true, count = 8) }       // shown at once
        advance(1_000)
        expectState { copy(isLiked = false, count = 7) }      // back to what it was
        expectEffect(LikeEffect.Failed)
    }
}
```

## 6. A form: validation and errors

**Situation.** Fields, rules, an error under a field, a submit button.

**Rule.**

- Fields are state, and the **intent carries the raw text**. Trimming and validating happen in the logic.
- What is valid, and which error to show, are **computed properties**. They are never stored flags.
- Decide *when* to show an error with data, not with the UI:

  | When to show | State |
  |---|---|
  | While typing, from the first character | an error computed from the text |
  | After the user left the field | a `touched: Set<Field>` in the state, error computed from text and `touched` |
  | Only after a submit | a `submitted: Boolean` in the state |

- A rule the server decides (email taken) comes back as a **typed failure** in the state, and is cleared when
  the field changes. The screen owns the text, not the logic.
- Submit is `Drop` at level 2, or `isSending` plus a guard at level 1. See `ReportLogic`.
- Never navigate from the state. A successful submit is an effect.

```kotlin
data class SignUpState(
    val email: String = "",
    val password: String = "",
    val submitted: Boolean = false,                       // the user pressed the button once
    val isSending: Boolean = false,
    val failure: SignUpFailure? = null,                   // what the server said, as a type
) {
    val emailError: EmailError?
        get() = if (submitted && !email.trim().contains('@')) EmailError.NotAnEmail else null
    val passwordError: PasswordError?
        get() = if (submitted && password.length < MIN_PASSWORD) PasswordError.TooShort else null
    val isValid: Boolean
        get() = email.trim().contains('@') && password.length >= MIN_PASSWORD

    companion object { const val MIN_PASSWORD = 8 }
}

class SignUpLogic(private val accounts: AccountRepository) :
    Logic<SignUpState, SignUpIntent, SignUpEffect> {
    private typealias Scope = StoreScope<SignUpState, SignUpEffect>

    override fun Scope.onIntent(intent: SignUpIntent) {
        when (intent) {
            // The server's answer belongs to the text it answered: editing the field clears it.
            is SignUpIntent.EditEmail -> setState { copy(email = intent.text, failure = null) }
            is SignUpIntent.EditPassword -> setState { copy(password = intent.text) }
            SignUpIntent.Submit -> submit()
        }
    }

    private fun Scope.submit() {
        if (state.isSending) return
        setState { copy(submitted = true) }                // the first press reveals the errors
        if (!state.isValid) return
        val email = state.email.trim()
        val password = state.password
        setState { copy(isSending = true, failure = null) }
        launch {
            safeCall { accounts.signUp(email, password) }
                .onSuccess { emit(SignUpEffect.Done) }
                .onFailure { error ->
                    val failure = if (error is EmailTakenException) SignUpFailure.EmailTaken else SignUpFailure.Unknown
                    setState { copy(failure = failure) }
                }
            setState { copy(isSending = false) }
        }
    }
}
```

The button stays enabled while the form is invalid: the first press shows why. A disabled button that gives
no reason is worse. If the product wants it disabled, use `isValid`.

**Test.**

```kotlin
@Test
fun errorsAppearOnlyAfterTheFirstSubmit() = runTest {
    val accounts = FakeAccounts()
    storeTest(SignUpState(), SignUpLogic(accounts)) {
        send(SignUpIntent.EditEmail("nope"))
        expectState { copy(email = "nope") }
        assertNull(state.emailError)                      // not yet

        send(SignUpIntent.Submit)
        expectState { copy(submitted = true) }
        assertEquals(EmailError.NotAnEmail, state.emailError)
        assertEquals(0, accounts.calls)                   // an invalid form never calls the repository
    }
}

@Test
fun theComputedRulesNeedNoStore() {
    assertEquals(false, SignUpState(email = "a@b", password = "short").isValid)
    assertEquals(EmailError.NotAnEmail, SignUpState(email = "nope", submitted = true).emailError)
}
```

## 7. A multi-step flow

**Situation.** Steps (details → address → confirm), or a flow with exclusive modes (editing, submitting,
verifying, locked).

**Rule.**

- If the steps are only **which fields to show** and the data is shared, keep level 1 with a `step` field,
  and keep `step` in a keeper.
- If the modes **exclude each other and carry different data**, go to level 3: a sealed type of phases. An
  intent in the wrong phase does nothing, by construction.
- Back from a step is an intent (`Back`) that moves the phase. The system back button is the screen posting
  that intent, or an effect `Exit` when there is nothing to go back to.
- A request is work of its phase (`runOn`): it is cancelled when the state leaves the phase.

See `signin/` for a full flow at every form, and [migrations](migrations.md) for when to move.

```kotlin
sealed interface OrderPhase {
    data class Details(val name: String = "") : OrderPhase

    data class Address(val name: String, val city: String = "") : OrderPhase

    data class Confirm(val name: String, val city: String) : OrderPhase

    data class Sending(val name: String, val city: String) : OrderPhase

    data class Placed(val orderId: String) : OrderPhase
}

class OrderLogic(private val orders: OrderRepository) : Logic<OrderPhase, OrderIntent, OrderEffect> {
    private typealias Scope = StoreScope<OrderPhase, OrderEffect>

    override fun Scope.onIntent(intent: OrderIntent) {
        when (val phase = state) {
            is OrderPhase.Details -> when (intent) {
                is OrderIntent.EditName -> setState { phase.copy(name = intent.text) }
                OrderIntent.Next -> if (phase.name.isNotBlank()) setState { OrderPhase.Address(phase.name.trim()) }
                else -> Unit
            }
            is OrderPhase.Address -> when (intent) {
                is OrderIntent.EditCity -> setState { phase.copy(city = intent.text) }
                OrderIntent.Next -> if (phase.city.isNotBlank()) setState { OrderPhase.Confirm(phase.name, phase.city.trim()) }
                OrderIntent.Back -> setState { OrderPhase.Details(phase.name) }          // the data is kept
                else -> Unit
            }
            is OrderPhase.Confirm -> when (intent) {
                OrderIntent.Back -> setState { OrderPhase.Address(phase.name, phase.city) }
                OrderIntent.Place -> place(phase)
                else -> Unit
            }
            is OrderPhase.Sending, is OrderPhase.Placed -> Unit                         // nothing to do
        }
    }

    private fun Scope.place(phase: OrderPhase.Confirm) {
        setState { OrderPhase.Sending(phase.name, phase.city) }
        runOn(OrderPhase.Sending::class) { sending ->        // cancelled if the state leaves Sending
            safeCall { orders.place(sending.name, sending.city) }
                .onSuccess { orderId -> setState { OrderPhase.Placed(orderId) } }
                .onFailure {
                    setState { OrderPhase.Confirm(name, city) }                         // the data is kept
                    emit(OrderEffect.Failed)
                }
        }
    }
}
```

**Test.**

```kotlin
@Test
fun anIntentInTheWrongPhaseDoesNothing() = runTest {
    storeTest(OrderPhase.Details(), OrderLogic(FakeOrders())) {
        send(OrderIntent.EditCity("London"), OrderIntent.Back, OrderIntent.Place)
        expectNoItems()
    }
}

@Test
fun aFailedOrderReturnsToConfirmWithTheDataKept() = runTest {
    storeTest(OrderPhase.Confirm("Ada", "London"), OrderLogic(FakeOrders(fails = true))) {
        send(OrderIntent.Place)
        expectState { OrderPhase.Sending("Ada", "London") }
        advance(1_000)
        expectState { OrderPhase.Confirm("Ada", "London") }
        expectEffect(OrderEffect.Failed)
    }
}
```

## 8. Observing a source outside the screen

**Situation.** The session, a setting, a cart that other screens change.

**Rule.** The source is a `Flow` from a repository. A binder (`observe`) maps it into the state in `onStart`,
and has no other logic. The screen never owns the source, and never copies it into a field of its own.

- If it matters only while the screen is visible (a live count, a location), use `observeWhileSubscribed`.
- A change **to** the source is an intent that calls the repository. The state changes when the source emits
  back. That gives one writer, and two screens never disagree.
- If you need the change to appear before the repository confirms, that is case 5.

```kotlin
interface CartRepository {
    val items: Flow<List<CartItem>>                       // the cart now and every change, whoever makes it

    suspend fun remove(id: String)
}

class CartLogic(private val cart: CartRepository) : Logic<CartState, CartIntent, CartEffect> {
    private typealias Scope = StoreScope<CartState, CartEffect>

    override fun Scope.onStart() {
        observe(cart.items) { items -> setState { copy(items = items) } }
    }

    override fun Scope.onIntent(intent: CartIntent) {
        when (intent) {
            is CartIntent.Remove -> launch {
                // No state change here: it arrives through the flow, when the repository agrees.
                safeCall { cart.remove(intent.id) }.onFailure { emit(CartEffect.RemoveFailed) }
            }
        }
    }
}
```

**Test.** A fake repository exposes a `MutableStateFlow`:

```kotlin
@Test
fun theStateFollowsTheCart() = runTest {
    val cart = FakeCart(listOf(tea))                      // items = a MutableStateFlow
    storeTest(CartState(), CartLogic(cart)) {
        expectState { copy(items = listOf(tea)) }

        cart.current.value = listOf(tea, cake)            // another screen added one
        advance(0)
        expectState { copy(items = listOf(tea, cake)) }
    }
}
```

## 9. Arguments in, results out

**Situation.** A screen gets an id; a picker screen returns a choice.

**Arguments** are constructor parameters of the logic, passed through the ViewModel factory. They are not
state (they never change), and not intents. If the argument can change while the screen lives (a deep link to
another item), it is an intent (`Open(id)`), and the id is state.

**Results.** The picked value comes back through the navigation layer, and the route turns it into an
**intent**. The logic never reads the navigation result itself, so it stays free of Android and navigation,
and the result is tested like any other intent.

**Going back.** Navigation and closing are **effects** (`Saved`, `OpenPicker(current)`). The route handles
them with the navigator. The state never holds "should navigate".

```kotlin
sealed interface AddressIntent {
    data object Change : AddressIntent

    data class Picked(val address: String) : AddressIntent      // the picker's answer

    data object Save : AddressIntent
}

sealed interface AddressEffect {
    data class OpenPicker(val current: String) : AddressEffect

    data object Saved : AddressEffect
}

class AddressLogic(
    private val orderId: String,                          // an argument: a parameter, not state
    private val addresses: AddressRepository,
) : Logic<AddressState, AddressIntent, AddressEffect> {
    private typealias Scope = StoreScope<AddressState, AddressEffect>

    override fun Scope.onIntent(intent: AddressIntent) {
        when (intent) {
            AddressIntent.Change -> emit(AddressEffect.OpenPicker(state.address))
            is AddressIntent.Picked -> setState { copy(address = intent.address) }
            AddressIntent.Save -> save()
        }
    }

    private fun Scope.save() {
        if (state.isSaving || state.address.isBlank()) return
        val address = state.address
        setState { copy(isSaving = true) }
        launch {
            safeCall { addresses.save(orderId, address) }.onSuccess { emit(AddressEffect.Saved) }
            setState { copy(isSaving = false) }
        }
    }
}
```

The route is the only code that knows about navigation:

```kotlin
@Composable
fun AddressScreen(
    store: Store<AddressState, AddressIntent, AddressEffect>,
    pickedAddress: String?,                               // the picker's result, as navigation hands it back
    openPicker: (current: String) -> Unit,
    onSaved: () -> Unit,
) {
    LaunchedEffect(pickedAddress) {
        pickedAddress?.let { store.postIntent(AddressIntent.Picked(it)) }
    }
    store.CollectEffects { effect ->
        when (effect) {
            is AddressEffect.OpenPicker -> openPicker(effect.current)
            AddressEffect.Saved -> onSaved()
        }
    }
    AddressContent(state = store.collectAsState(), postIntent = store::postIntent)
}
```

**Test.**

```kotlin
@Test
fun theAnswerComesBackAsAnIntent() = runTest {
    storeTest(AddressState(address = "Old street"), AddressLogic("o1", FakeAddresses())) {
        send(AddressIntent.Change)
        expectEffect(AddressEffect.OpenPicker("Old street"))
        send(AddressIntent.Picked("New street"))
        expectState { copy(address = "New street") }
    }
}
```

## 10. A dialog or sheet with logic

**Situation.** A confirm dialog, a report sheet, an inline editor.

**Rule.** It depends on what the part does:

| The part | Use |
|---|---|
| Only displays, and its answer is one tap | State in the parent (`confirmDelete: PostId?`) and intents `Confirm` / `Dismiss`. No slot |
| Has its own IO or several fields, and its work must stop when it closes | **Slot** |
| Is also its own screen elsewhere | Slot with that screen's logic, unchanged |

A slot's state in the parent is `null` when closed, so "is it open" is state and survives rotation. The
child's effects are its outputs, and the parent decides what they mean (`Sent` closes the slot and shows a
message). The parent gets the child's logic from a factory, since each opening needs a new one
([DI](guide.md#12-dependency-injection-and-the-size-of-a-viewmodel)). See `FeedLogic`.

```kotlin
class FeedLogic(
    private val repository: PostRepository,
    reportLogic: ReportLogic.Factory,
) : ActionLogic<FeedState, FeedIntent, Unit, FeedEffect>() {
    private typealias Scope = StoreScope<FeedState, FeedEffect>

    private val report: Slot<FeedState, FeedEffect, String, ReportState, ReportIntent, ReportEffect> = Slot(
        get = FeedState::report,                          // null while closed
        set = { copy(report = it) },
        create = { postId -> Hosted(ReportState(), reportLogic.create(postId)) },
        onEffect = { effect ->                            // the child's outputs
            when (effect) {
                ReportEffect.Sent -> {
                    report.close()
                    emit(FeedEffect.Message("Report sent"))
                }
                is ReportEffect.Failed -> emit(FeedEffect.Message(effect.message))
            }
        },
    )

    override fun Scope.onStart() {
        report.attach(this)                               // always attach in the parent's onStart
        dispatch(Unit)
    }

    override fun Scope.toAction(intent: FeedIntent): Unit? {
        when (intent) {
            is FeedIntent.OpenReport -> report.open(intent.postId)
            is FeedIntent.Report -> report.postIntent(intent.intent)     // dropped when closed
            FeedIntent.CloseReport -> report.close()                     // cancels a send in flight
            FeedIntent.Refresh -> return Unit
        }
        return null
    }
    // …
}
```

**Test.**

```kotlin
@Test
fun closingTheSheetCancelsAReportBeingSent() = runTest {
    storeTest(FeedState(), FeedLogic(posts)) {
        loaded()                                          // the feed's first load
        send(
            FeedIntent.OpenReport("p1"),
            FeedIntent.Report(ReportIntent.SelectReason(ReportReason.Spam)),
            FeedIntent.Report(ReportIntent.Send),
        )
        skip(3)                                           // opened, reason, sending
        send(FeedIntent.CloseReport, FeedIntent.Report(ReportIntent.EditComment("too late")))
        expectState { copy(report = null) }               // the late intent is dropped

        advance(5_000)                                    // no "sent", no write into a sheet that is gone
    }
}
```

## 11. Timers, countdowns, polling

**Situation.** A resend-code countdown, a poll for a payment status, a debounce that is not a search.

**Rule.**

- A timer that belongs to **a mode** is `whileIn(Phase::class) { … }`: it starts on entering and is cancelled
  on leaving, and its `setState` does nothing after the phase is left. The remaining seconds are **state in
  the phase**.
- A timer that belongs to **the visible screen** is `whileSubscribed`.
- A timer that belongs to **one request** is `delay` inside an action.
- Never use `delay` in a UI composable to drive the logic.

```kotlin
sealed interface ResendState {
    data object Ready : ResendState

    data class Waiting(val secondsLeft: Int) : ResendState
}

class ResendLogic(private val codes: CodeSender) : Logic<ResendState, ResendIntent, ResendEffect> {
    private typealias Scope = StoreScope<ResendState, ResendEffect>

    override fun Scope.onStart() {
        whileIn(ResendState.Waiting::class) { waiting ->     // starts on entering, stops on leaving
            var left = waiting.secondsLeft
            while (left > 0) {
                delay(1_000)
                left -= 1
                setState { copy(secondsLeft = left) } ?: return@whileIn   // null: the phase was left
            }
            setState { ResendState.Ready }
        }
    }

    override fun Scope.onIntent(intent: ResendIntent) {
        when (intent) {
            ResendIntent.Resend -> if (state == ResendState.Ready) resend()
        }
    }

    private fun Scope.resend() {
        setState { ResendState.Waiting(30) }
        launch {
            safeCall { codes.send() }.onFailure {
                setState { ResendState.Ready }               // leaving Waiting stops the countdown
                emit(ResendEffect.Failed)
            }
        }
    }
}
```

**Test.**

```kotlin
@Test
fun resendingStartsACountdownThatEndsReady() = runTest {
    storeTest(ResendState.Ready, ResendLogic(FakeCodes())) {
        send(ResendIntent.Resend)
        expectState { ResendState.Waiting(30) }
        advance(1_000)
        expectState { ResendState.Waiting(29) }
        advance(29_000)
        skipAll()
        assertEquals(ResendState.Ready, state)
    }
}

@Test
fun aFailedSendStopsTheCountdown() = runTest {
    storeTest(ResendState.Ready, ResendLogic(FakeCodes(fails = true))) {
        send(ResendIntent.Resend)
        skipAll()
        advance(60_000)
        expectNoItems()                                   // no ticks after the phase was left
    }
}
```

## 12. Selection mode in a list

**Situation.** Long-press to select items, then act on them.

**Rule.** The selection is a `Set<Id>` in the state, not a flag on each item, so the items stay as they came
and the screen computes `isSelecting`. The action for delete gathers what it needs before anything suspends,
changes the state at once, and puts back exactly what it removed if the call fails.

```kotlin
data class SelectionState(
    val rows: List<Row> = emptyList(),
    val selection: Set<String> = emptySet(),
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
}

class SelectionLogic(private val repository: RowRepository) :
    Logic<SelectionState, SelectionIntent, SelectionEffect> {
    private typealias Scope = StoreScope<SelectionState, SelectionEffect>

    override fun Scope.onIntent(intent: SelectionIntent) {
        when (intent) {
            is SelectionIntent.Toggle -> setState {
                copy(selection = if (intent.id in selection) selection - intent.id else selection + intent.id)
            }
            SelectionIntent.DeleteSelected -> deleteSelected()
        }
    }

    private fun Scope.deleteSelected() {
        val removed = state.rows.filter { it.id in state.selection }      // gathered before anything suspends
        if (removed.isEmpty()) return
        val ids = removed.map { it.id }.toSet()
        setState { copy(rows = rows - removed.toSet(), selection = emptySet()) }
        launch {
            safeCall { repository.delete(ids) }.onFailure {
                setState { copy(rows = (rows + removed).sortedBy { it.id }) }   // only what was removed
                emit(SelectionEffect.DeleteFailed)
            }
        }
    }
}
```

Put back only the removed rows, and not the whole earlier state: the list may have changed meanwhile.
Name the constructor parameter for what it is (`repository`): a `rows` parameter would hide the state's
`rows` inside `setState { }`.

**Test.**

```kotlin
@Test
fun aFailedDeleteBringsTheRowsBack() = runTest {
    storeTest(SelectionState(rows, selection = setOf("1")), SelectionLogic(FakeRows(fails = true))) {
        send(SelectionIntent.DeleteSelected)
        expectState { copy(rows = rows.drop(1), selection = emptySet()) }   // shown at once
        advance(1_000)
        expectState { copy(rows = this@SelectionLogicTest.rows) }          // and back
        expectEffect(SelectionEffect.DeleteFailed)
    }
}
```

## 13. Platform calls: permissions, share, haptics

**Situation.** The logic needs something that only the platform can do.

**Rule.** Two directions:

- **The logic asks the screen** with an effect (`RequestPermission`, `Share(text)`, `Haptic`). The route does
  it and answers with an **intent** (`PermissionResult(granted)`). The logic stays free of the platform, and
  both directions are testable.
- **The logic needs a capability that is not UI** (a clipboard, a clock, a location). It is a dependency
  behind an interface in the constructor, with a fake in tests.

The state keeps what the screen must still show after a rotation (`permission`). The request itself is the
effect.

```kotlin
enum class Permission { Unknown, Granted, Denied }

sealed interface ShareIntent {
    data object ShareClicked : ShareIntent

    data class PermissionResult(val granted: Boolean) : ShareIntent
}

sealed interface ShareEffect {
    data object RequestPermission : ShareEffect         // asked of the screen: only the platform can show it
}

class ShareLogic(private val location: LocationProvider) : Logic<ShareState, ShareIntent, ShareEffect> {
    private typealias Scope = StoreScope<ShareState, ShareEffect>

    override fun Scope.onIntent(intent: ShareIntent) {
        when (intent) {
            ShareIntent.ShareClicked -> when (state.permission) {
                Permission.Granted -> share()
                Permission.Unknown, Permission.Denied -> emit(ShareEffect.RequestPermission)
            }
            is ShareIntent.PermissionResult -> {
                setState { copy(permission = if (intent.granted) Permission.Granted else Permission.Denied) }
                if (intent.granted) share()
            }
        }
    }

    private fun Scope.share() {
        if (state.isSharing) return
        setState { copy(isSharing = true) }
        launch {
            safeCall { location.current() }.onSuccess { place -> setState { copy(sharedAt = place) } }
            setState { copy(isSharing = false) }
        }
    }
}
```

The route answers the effect. `requestLocation` is the app's own wrapper over the platform's permission API:

```kotlin
store.CollectEffects { effect ->
    when (effect) {
        ShareEffect.RequestPermission -> store.postIntent(ShareIntent.PermissionResult(requestLocation()))
    }
}
```

**Test.**

```kotlin
@Test
fun withoutPermissionTheLogicAsksTheScreen() = runTest {
    storeTest(ShareState(), ShareLogic(FakeLocation())) {
        send(ShareIntent.ShareClicked)
        expectEffect(ShareEffect.RequestPermission)
        send(ShareIntent.PermissionResult(granted = true))
        expectState { copy(permission = Permission.Granted) }    // and it shares at once
    }
}
```

## 14. Reacting to the lifecycle

**Situation.** Refresh when the screen comes back, stop something when it leaves.

**Rule.**

- Work that should stop while the screen is hidden: `whileSubscribed` / `observeWhileSubscribed`. It follows
  the screen's visibility with a timeout, so a rotation does not restart it. See `LikeLogic.onStart`.
- A one-time action on every return (refresh on resume): the route posts an intent (`Resumed`) from
  `LifecycleEventEffect`. The logic decides whether it matters, for example by ignoring it while the data is
  fresh. The time it needs comes in as a function, so a test controls it.
- Never put `onResume` on the ViewModel.

```kotlin
data class FreshState(
    val items: ContentState<List<String>> = ContentState.Idle,
    val loadedAt: Long? = null,                          // internal: when it was last loaded
)

class FreshLogic(
    private val source: Source,
    private val now: () -> Long,                         // a dependency, not System.currentTimeMillis()
) : Logic<FreshState, FreshIntent, Nothing> {
    private typealias Scope = StoreScope<FreshState, Nothing>

    override fun Scope.onIntent(intent: FreshIntent) {
        when (intent) {
            FreshIntent.Resumed -> {
                val loadedAt = state.loadedAt
                if (loadedAt == null || now() - loadedAt > STALE_MILLIS) load()
            }
        }
    }

    private fun Scope.load() {
        setState { copy(items = items.reload(), loadedAt = now()) }
        launch {
            val result = safeCall { source.load() }
            setState { copy(items = result.toContentState()) }
        }
    }

    companion object {
        const val STALE_MILLIS = 60_000L
    }
}

@Composable
fun FreshRoute(store: Store<FreshState, FreshIntent, Nothing>) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { store.postIntent(FreshIntent.Resumed) }
    // …
}
```

**Test.**

```kotlin
@Test
fun aFreshResumeIsIgnored() = runTest {
    storeTest(FreshState(), FreshLogic(FakeSource(), now = { testScheduler.currentTime })) {
        send(FreshIntent.Resumed)
        skipAll()
        send(FreshIntent.Resumed)                         // still fresh
        expectNoItems()
        advance(61_000)
        send(FreshIntent.Resumed)                         // stale: loads again
        expectState { copy(items = ContentState.Loading(listOf("a")), loadedAt = 61_000) }
        skipAll()
    }
}

@Test
fun theCountIsLiveOnlyWhileTheCardIsOnScreen() = runTest {
    storeTest(LikeState(isLiked = false, count = 12), LikeLogic("p1", posts)) {
        advance(10 * FakePostRepository.LIVE_LIKE_MILLIS)
        expectNoItems()                                   // no one looks: nothing is watched

        val card = subscribe()                            // the screen is visible
        advance(FakePostRepository.LIVE_LIKE_MILLIS)
        expectState { copy(count = 13) }

        card.cancel()                                     // scrolled away: it goes on through the timeout, then stops
        advance(DEFAULT_STOP_TIMEOUT_MILLIS + 10 * FakePostRepository.LIVE_LIKE_MILLIS)
        expectState { copy(count = 14) }
    }
}
```

## 15. Where an error goes

| The failure | Goes to | Example |
|---|---|---|
| The user can read it and it stays until they act | **State** (`ContentState.Error`, a typed `failure`) | The feed did not load |
| The user should be told once, and the screen is fine | **Effect** (a message) | A follow failed and was rolled back |
| The user cannot do anything about it, and the logic is correct | `onError` (log, report) | A bug in an observer |
| A coding mistake (`check`, `require`) | Reported by `onError`; in tests it throws, so the test fails | The slot was not attached |

Rules:

- **Every `safeCall` ends as a state or an effect.** A `safeCall` whose failure is ignored is a bug, and a
  reviewer rejects it.
- **Do not show `error.message` to users.** Map the error to a typed reason in the logic (`NoNetwork`,
  `NotFound`), and let the UI own the text. Raw messages stay in logs.
- **Retry** is an intent. A failed result keeps what is shown (case 2) when the screen can still be used.
- **Cancellation is not an error.** `safeCall` rethrows it, so a cancelled search never reaches the user.

A typed error is the kit's `Failure`: a repository's `safeCall` maps whatever the data layer threw to one
`Failure` (field errors for a form, a backend status such as `status.error = "NOT_FOUND"`, a message), so the
logic never sees a transport exception. The state keeps it in the `ContentState.Error` it already has, and the
screen resolves its text where it is drawn: `failure.toUiString()` is `Localized("failure.not_found", …)`, which
an app with key-based translations translates and every other app shows with an English fallback.

```kotlin
// In the repository: the one place that knows the transport
safeCall { api.posts().also { check(it.isSuccessful) { it.toFailure() } } }

// In the logic
setState { copy(posts = result.toContentState()) }                 // Result<T>.toContentState(): Error(failure)

// In the UI
val text = (state.posts as? ContentState.Error)?.failure?.toUiString()?.resolve()
```

A screen that needs a different reaction per reason (a retry button for `NoNetwork`, none for `NotFound`)
matches on `failure.status.error` in its logic and keeps the decision in the state as a plain value.

**Test.** The mapper is a plain function in the repository's test: `assertEquals("NOT_FOUND", response404.toFailure().status.error)`.

## 16. One piece per list item

**Situation.** A like button on every card, a download button on every row.

**Rule.** When the piece does its own IO, it is a **component ViewModel** hosted with `ItemScope`, keyed by
the item's id. Its lifetime is the one `ItemViewModels` gives it:

- **On screen:** it lives. Scrolled away, it lives **only while its work runs**: a request that is being sent
  is not cancelled by a scroll. When the work ends it is let go, and its state with it.
- **Work** is what `Store.isBusy` says: launched work, an action, the work of a phase. A binder (`observe`,
  `whileSubscribed`) is not work, since it never ends.
- An item that leaves the list is cleared at once, working or not.

So the number of live ViewModels is the items on screen plus those with a request in flight, however long the
list grows. The state of a ViewModel that was let go is gone: what a piece of work produced belongs in the
list's data source (a repository or a cache) before it ends, and the item reads it from there when it comes
back. Keeping that source current is the data layer's job, not this architecture's.

The list passes only the **initial values**, and the component then owns its state while it lives. A refresh
of the list does not reset it. If the list must be the source of truth, the logic stays in the list screen
and the item is a stateless row with an intent carrying the id.

| The row | Use |
|---|---|
| Only displays; actions go to the list's logic with an id | Stateless row |
| Does its own IO, and the list does not need its result | Component ViewModel (`LikeLogic`) |
| Does its own IO, and the list needs the result | Stateless row, logic in the list |

```kotlin
class LikeViewModel(post: Post, posts: PostRepository) :
    ViewModel(),
    StoreOwner<LikeState, LikeIntent, LikeEffect> {
    override val store = store(storeFactory, LikeState(post.isLiked, post.likes), LikeLogic(post.id, posts))
}

// The list keeps the item ViewModels of the posts it has, and lets an item go when it is off screen and idle.
val cards = rememberItemViewModels(keys = posts.map { it.id })
LazyColumn {
    items(posts, key = { it.id }) { post ->
        ItemScope(cards, key = post.id) {
            LikeButton(post)                              // inside, viewModel { … } is this card's own
        }
    }
}

@Composable
private fun LikeButton(post: Post) {
    val like = viewModel { LikeViewModel(post, posts) }.store
    val state by like.collectAsState()
    TextButton(onClick = { like.postIntent(LikeIntent.Toggle) }) {
        Text("${if (state.isLiked) "♥" else "♡"} ${state.count}")
    }
}
```

**Test.** `LikeLogic` alone, as its own store (case 5). The lifetime rules are `ItemViewModelsTest`: an idle
item is let go when it leaves the screen, a busy one lives until its work ends, one that comes back keeps its
ViewModel, one that leaves the list is cleared at once. The list's test does not know about any of it.

## 17. Draft that survives the process

**Situation.** The user typed a long text, and the app is killed in the background.

**Rule.** A keeper saves a **part** (what the user typed or chose), and merges it back into the initial
state. Never loaded data, never a phase. The keeper writes the first change at once and merges those that
follow within 300 ms into one write of the last value, so typing is not encoded on every key. See
`createpost/`, and [guide 11.4](guide.md#114-internal-state-and-process-death).

```kotlin
@Serializable
data class SavedDraft(
    val text: String,
    val pollOn: Boolean,
    val pollOptions: List<String>,
    val tags: List<String>,
)

fun SavedStateHandle.draftKeeper() = keeper<CreatePostState, SavedDraft>(
    key = "draft",
    serializer = SavedDraft.serializer(),
    save = { state -> SavedDraft(state.body.text, state.poll.isOn, state.poll.options, state.tags.selected) },
    restore = { draft ->                                  // merged into the initial state
        copy(
            body = body.copy(text = draft.text),
            poll = poll.copy(isOn = draft.pollOn, options = draft.pollOptions),
            tags = tags.copy(selected = draft.tags),
        )
    },
)

class CreatePostViewModel(posts: PostRepository, savedState: SavedStateHandle) :
    ViewModel(),
    StoreOwner<CreatePostState, CreatePostIntent, CreatePostEffect> {
    override val store = store(storeFactory, CreatePostState(), CreatePostLogic(posts), keeper = savedState.draftKeeper())
}
```

**Test.** A saved value that no longer decodes is dropped, and the screen starts clean:

```kotlin
@Test
fun aDraftComesBackAfterTheProcessDies() = runTest {
    val handle = SavedStateHandle()
    storeTest(CreatePostState(), CreatePostLogic(posts), keeper = handle.draftKeeper()) {
        send(CreatePostIntent.Body(BodyIntent.Edit("hello")))
        skipAll()
    }
    val restored = testStoreFactory().create(
        scope = backgroundScope,
        initialState = CreatePostState(),
        logic = CreatePostLogic(posts),
        keeper = handle.draftKeeper(),
    )
    assertEquals("hello", restored.state.value.body.text)
}
```

## 18. Testing recipes

Each recipe is a few lines. The fakes are small classes with a deliberate latency (a `delay(1_000)`), so a
test can say what is true before and after the answer.

**A time-based rule.** `advance(ms)`: the clock is virtual.

```kotlin
send(ResendIntent.Resend)
expectState { ResendState.Waiting(30) }
advance(1_000)
expectState { ResendState.Waiting(29) }
```

**The state before `onStart`.**

```kotlin
storeTest(ProfileState(), ProfileLogic("u1", profiles), start = false) {
    assertEquals(ContentState.Idle, state.profile)       // nothing has run
    start()
    expectState { copy(profile = ContentState.Loading()) }
}
```

**What the screen sees through `mapState`.**

```kotlin
val store = testStoreFactory().create(backgroundScope, CheckoutState(), CheckoutLogic(cart)).mapState { it.toUi() }
store.postIntent(CheckoutIntent.Pay)
runCurrent()
assertEquals(CheckoutUi(rows = emptyList(), total = "0", canPay = false), store.state.value)
```

**Work that runs only while visible.** `subscribe()` is the visible screen.

```kotlin
val card = subscribe()
advance(FakePostRepository.LIVE_LIKE_MILLIS)
expectState { copy(count = 13) }
card.cancel()
```

**A failure.** A fake whose method throws, then the state and the effect:

```kotlin
send(LikeIntent.Toggle)
expectState { copy(isLiked = true, count = 8) }
advance(1_000)
expectState { copy(isLiked = false, count = 7) }
expectEffect(LikeEffect.Failed)
```

**Nothing happens.** `expectNoItems()` after the intent. It is the only way to test a guard.

```kotlin
send(OrderIntent.EditCity("London"))                  // in the Details phase
expectNoItems()
```

**A computed property, and a mapper.** Plain tests, no store:

```kotlin
assertEquals(true, ReportState(reason = ReportReason.Spam).canSend)
assertEquals(CheckoutUi(…), CheckoutState(…).toUi())
```

**A rule that is the same at several levels.** One abstract suite, one subclass per implementation
(`SignInBehaviourTest`):

```kotlin
abstract class SignInBehaviourTest {
    abstract fun store(auth: AuthRepository, scope: CoroutineScope): Store<SignInUi, SignInIntent, SignInEffect>
    // … the behaviours, asserted on SignInUi and effects only
}

class SignInLevel2Test : SignInBehaviourTest() {
    override fun store(auth: AuthRepository, scope: CoroutineScope) =
        testStoreFactory().create(scope, SignInLevel2State(), SignInLevel2Logic(auth)).mapState { it.toUi() }
}
```

**A slice alone.** The slice's logic is its own store. The parent's test covers routing, the cross-slice
rules and the child's outputs only.

A good test of a behaviour has four parts: the starting state, the intents, the expected states and effects
in order, and a fake with a deliberate latency. If a test needs a real clock, a real network, or the Android
framework, the logic is holding something it should have received.
