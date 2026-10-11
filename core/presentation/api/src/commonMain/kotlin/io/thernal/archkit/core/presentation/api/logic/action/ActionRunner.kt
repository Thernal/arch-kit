package io.thernal.archkit.core.presentation.api.logic.action

import io.thernal.archkit.core.presentation.api.store.StoreScope
import io.thernal.archkit.core.presentation.api.store.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The running actions of one host, by key. Called on the host's thread only. */
internal class ActionRunner {
    private val running = mutableMapOf<Any, Job>()
    private val queues = mutableMapOf<Any, Mutex>()

    fun run(
        scope: StoreScope<*, *>,
        policy: Policy,
        block: suspend () -> Unit,
    ) {
        when (policy) {
            Policy.Parallel -> scope.launch { block() }

            is Policy.Latest -> {
                running[policy.key]?.cancel()
                track(key = policy.key, job = scope.launch { block() })
            }

            is Policy.Ignore -> if (running[policy.key]?.isActive != true) {
                track(key = policy.key, job = scope.launch { block() })
            }

            is Policy.Queue -> {
                val queue = queues.getOrPut(policy.key) { Mutex() }
                scope.launch { queue.withLock { block() } }
            }
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
}
