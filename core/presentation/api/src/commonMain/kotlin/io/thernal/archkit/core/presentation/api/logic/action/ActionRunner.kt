package io.thernal.archkit.core.presentation.api.logic.action

import io.thernal.archkit.core.presentation.api.store.observer.ActionOutcome
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.TracedScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * The running actions of one host, by key, and what becomes of each one, told to the store's observers.
 * Called on the host's thread only.
 */
internal class ActionRunner {
    private val running = mutableMapOf<Any, Job>()
    private val queues = mutableMapOf<Any, Mutex>()

    /** Actions of a queue's key that are running or waiting: a queue's lock is free until its first one runs. */
    private val queued = mutableMapOf<Any, Int>()

    fun run(
        scope: StoreScope<*, *>,
        action: Any,
        policy: Policy,
        block: suspend () -> Unit,
    ) {
        val report = Report(scope = scope as? TracedScope<*, *>, action = action, policy = policy)
        when (policy) {
            Policy.Parallel -> start(scope = scope, report = report, block = block)

            is Policy.Latest -> {
                running[policy.key]?.cancel()
                track(key = policy.key, job = start(scope = scope, report = report, block = block))
            }

            is Policy.Ignore -> if (running[policy.key]?.isActive == true) {
                report.outcome(ActionOutcome.Dropped)
            } else {
                track(key = policy.key, job = start(scope = scope, report = report, block = block))
            }

            is Policy.Queue -> enqueue(key = policy.key, scope = scope, report = report, block = block)
        }
    }

    private fun start(
        scope: StoreScope<*, *>,
        report: Report,
        block: suspend () -> Unit,
    ): Job {
        report.started()
        val job = scope.launch { block() }
        job.invokeOnCompletion(report::ended)
        return job
    }

    private fun enqueue(
        key: Any,
        scope: StoreScope<*, *>,
        report: Report,
        block: suspend () -> Unit,
    ) {
        val queue = queues.getOrPut(key) { Mutex() }
        val ahead = queued[key] ?: 0
        queued[key] = ahead + 1
        if (ahead > 0) {
            report.outcome(ActionOutcome.Queued)
        }
        val job = scope.launch {
            queue.withLock {
                report.started()
                block()
            }
        }
        job.invokeOnCompletion { cause ->
            val left = (queued[key] ?: 1) - 1
            if (left > 0) {
                queued[key] = left
            } else {
                queued.remove(key)
            }
            report.ended(cause)
        }
    }

    private fun track(
        key: Any,
        job: Job,
    ) {
        running[key] = job
        job.invokeOnCompletion {
            if (running[key] === job) {
                running.remove(key)
            }
        }
    }

    private class Report(
        private val scope: TracedScope<*, *>?,
        private val action: Any,
        private val policy: Policy,
    ) {
        private var mark = TimeSource.Monotonic.markNow()

        fun started() {
            mark = TimeSource.Monotonic.markNow()
            outcome(ActionOutcome.Started)
        }

        fun ended(cause: Throwable?) {
            val duration = mark.elapsedNow()
            val outcome = when (cause) {
                null -> ActionOutcome.Finished(duration)
                is CancellationException -> ActionOutcome.Cancelled(duration)
                else -> ActionOutcome.Failed(error = cause, duration = duration)
            }
            outcome(outcome)
        }

        fun outcome(outcome: ActionOutcome) {
            scope?.reportAction(action = action, policy = policy, outcome = outcome)
        }
    }
}
