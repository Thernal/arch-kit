package io.thernal.archkit.core.domain

import io.thernal.archkit.core.domain.concurrent.Debouncer
import io.thernal.archkit.core.domain.concurrent.Throttler
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.failure.FieldError
import io.thernal.archkit.core.domain.model.asEnum
import io.thernal.archkit.core.domain.safecall.FailureReporter
import io.thernal.archkit.core.domain.safecall.FailureReporting
import io.thernal.archkit.core.domain.safecall.safeCall
import io.thernal.archkit.core.domain.safecall.safeCallFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

private enum class Role { ADMIN, MEMBER }

class DomainTest {
    private val reported = mutableListOf<Pair<Throwable, Boolean>>()

    init {
        FailureReporting.reporter = FailureReporter { error, isUnexpected -> reported += error to isUnexpected }
    }

    @AfterTest
    fun resetReporter() {
        FailureReporting.reporter = FailureReporter { _, _ -> }
    }

    @Test
    fun safeCallReturnsFailuresAsTheyAreAndWrapsTheRest() {
        runTest {
            val expected = Failure(errors = listOf(FieldError(field = "email", error = "TAKEN")))
            val handled = mutableListOf<Failure>()

            val known = safeCall(handleError = { handled += it }) { throw expected }
            val unknown = safeCall<Int> { error("boom") }

            assertEquals(expected = expected, actual = known.exceptionOrNull())
            assertEquals(expected = "boom", actual = unknown.exceptionOrNull()?.message)
            assertTrue(unknown.exceptionOrNull() is Failure)
            assertEquals(expected = listOf(expected), actual = handled)
            assertEquals(expected = listOf(false, true), actual = reported.map { it.second })
        }
    }

    @Test
    fun safeCallRethrowsCancellation() {
        runTest {
            assertFailsWith<CancellationException> { safeCall<Int> { throw CancellationException("stop") } }
        }
    }

    @Test
    fun safeCallFlowEndsWithTheFailure() {
        runTest {
            val results = safeCallFlow {
                flow {
                    emit(1)
                    error("broken")
                }
            }.toList()

            assertEquals(expected = 1, actual = results.first().getOrNull())
            assertTrue(results.last().exceptionOrNull() is Failure)
        }
    }

    @Test
    fun theDebouncerRunsOnlyTheLastAction() {
        runTest {
            val debouncer = Debouncer(duration = 300.milliseconds, scope = this)
            val ran = mutableListOf<String>()

            listOf("a", "ab", "abc").forEach { query -> debouncer { ran += query } }
            advanceUntilIdle()

            assertEquals(expected = listOf("abc"), actual = ran)
        }
    }

    @Test
    fun theThrottlerDropsCallsInsideItsWindow() {
        val time = TestTimeSource()
        val throttler = Throttler(duration = 1_000.milliseconds, timeSource = time)

        assertTrue(throttler.tryAcquire())
        assertFalse(throttler.tryAcquire())
        time += 1_000.milliseconds
        assertTrue(throttler.tryAcquire())
    }

    @Test
    fun aStringNamesAnEnumEntry() {
        assertEquals(expected = Role.ADMIN, actual = "admin".asEnum<Role>())
        assertEquals(expected = null, actual = "owner".asEnum<Role>())
    }
}
