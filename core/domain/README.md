# core/domain and core/event

Pure Kotlin building blocks for every feature's domain and data layers. Package
`io.thernal.archkit.core.domain`; the event bus in `io.thernal.archkit.core.event.api.domain`.

## Failure and safeCall

`Failure(errors, status, message, cause)` is the one exception a repository or use case lets out:
`errors` are field errors (for a form), `status` the backend's code and name, `message` for a log.

```kotlin
class PostRepositoryImpl(private val api: PostApi) : PostRepository {
    override suspend fun post(id: String): Result<Post> {
        return safeCall { api.post(id).toDomain() }
    }
}
```

`safeCall` returns `Result`: success, the `Failure` thrown, or anything else wrapped with `Failure.of(it)`.
Cancellation is rethrown, never a failure. `block` runs with a `SafeCallScope` context — a marker that a
guarded call is provably wrapped. `safeCallFlow { flow }` does the same for a stream: every value a success,
the first failure the last element.

Map transport errors to `Failure` at the edge. With network-kit:

```kotlin
suspend fun <T> apiCall(block: suspend () -> T): Result<T> {
    return safeCall {
        try {
            block()
        } catch (failure: NetworkException) {
            throw failure.toFailure()   // your one mapping: NetworkError.Http(body) → Failure(errors, status, message)
        }
    }
}
```

Every failure `safeCall` catches is reported to `FailureReporting.reporter` — set it once at startup to your
crash reporter or debug console. `isUnexpected` is true for anything that was not a `Failure`.

## Use cases

```kotlin
interface UseCase<Input, Output> { operator fun invoke(params: Input): Output }            // pure
interface SuspendUseCase<Input, Output> { suspend operator fun invoke(params: Input): Output }
interface FlowUseCase<Input, Output> { fun execute(params: Input): Flow<Output> }            // stream
```

A ViewModel calls a `FlowUseCase` as `useCase(params)` → `Flow<Result<Output>>` (core/presentation).

## Debouncer and Throttler

```kotlin
private val search = Debouncer(duration = 300.milliseconds, scope = viewModelScope)
private val submit = Throttler(duration = 1.seconds)

fun onQuery(query: String) = search { load(query) }       // the last of a burst, after it stops
fun onSubmit() = submit { save() }                        // the first; the rest are dropped for a second
```

## Events between features

```kotlin
data object SessionExpired : BaseEvent

eventBus.fire(SessionExpired)
eventBus.observe<SessionExpired>().collect { … }         // subtypes too
```

Events are not state: fired while nobody observes, they are gone. In a ViewModel use the event plugin
(`fireEvent`, `observeEvent`) — core/presentation. `EventWiring` binds one `EventBus` per app.
