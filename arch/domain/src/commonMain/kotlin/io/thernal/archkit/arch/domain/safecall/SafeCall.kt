package io.thernal.archkit.arch.domain.safecall

import io.thernal.archkit.arch.domain.failure.Failure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

/** Compile-time proof that a guarded call runs inside `safeCall`; it has no members. */
interface SafeCallScope

/**
 * Where failures caught by [safeCall] are reported — a crash reporter, a debug console. Set once at
 * startup ([FailureReporting.reporter]); by default nothing is reported.
 */
fun interface FailureReporter {
    /** [isUnexpected] is true for anything that was not already a [Failure]. */
    fun report(
        error: Throwable,
        isUnexpected: Boolean,
    )
}

object FailureReporting {
    var reporter: FailureReporter = FailureReporter { _, _ -> }
}

/**
 * Runs [block] and returns its value as a [Result]: success, or the [Failure] it threw — anything
 * else wrapped with `Failure.of` — after reporting it and handing it to [handleError]. Cancellation
 * is rethrown, never turned into a failure.
 */
suspend fun <T> safeCall(
    handleError: (suspend (Failure) -> Unit)? = null,
    block: suspend context(SafeCallScope) () -> T,
): Result<T> {
    return runSafeCall(block = block, onFailure = { failure -> handleError?.invoke(failure) })
}

@Suppress("TooGenericExceptionCaught") // The point of safeCall: nothing but cancellation escapes it.
suspend fun <T> runSafeCall(
    block: suspend context(SafeCallScope) () -> T,
    onFailure: suspend (Failure) -> Unit,
): Result<T> {
    return try {
        Result.success(with(Scope) { block() })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        val failure = Failure.of(error)
        report(error)
        onFailure(failure)
        Result.failure(failure)
    }
}

/** [safeCall] for a block that streams: every value as a success, the first failure as the last element. */
@Suppress("TooGenericExceptionCaught")
fun <T> safeCallFlow(block: context(SafeCallScope) () -> Flow<T>): Flow<Result<T>> {
    val source = try {
        with(Scope) { block() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        report(error)
        return flowOf(Result.failure(Failure.of(error)))
    }
    return source
        .map { Result.success(it) }
        .catch { error ->
            if (error is CancellationException) {
                throw error
            }
            report(error)
            emit(Result.failure(Failure.of(error)))
        }
}

private object Scope : SafeCallScope

private fun report(error: Throwable) {
    FailureReporting.reporter.report(error = error, isUnexpected = error !is Failure)
}
