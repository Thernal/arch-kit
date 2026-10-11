package io.thernal.archkit.core.presentation.api.logic.action

/**
 * What happens when an action starts while another with the same key is still running. Every policy that
 * makes actions meet takes its key: there is no default, because a default shared by a whole class of
 * actions makes "follow user B" cancel "follow user A" without a sign that anything is wrong.
 */
sealed interface Policy {
    /** Both run. Independent work: a like on one post and on another. */
    data object Parallel : Policy

    /** The running one is cancelled — its result is stale before it arrives. Search as you type, a filter. */
    data class Latest(
        val key: Any,
    ) : Policy

    /** The new one is ignored while one runs. Submit, pay, pull to refresh. */
    data class Ignore(
        val key: Any,
    ) : Policy

    /** The new one waits for the running one. Writes that build on each other: reorder, append. */
    data class Queue(
        val key: Any,
    ) : Policy
}
