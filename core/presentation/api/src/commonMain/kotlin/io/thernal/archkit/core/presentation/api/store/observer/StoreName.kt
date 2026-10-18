package io.thernal.archkit.core.presentation.api.store.observer

/** The logic a store hosts and which store of that logic it is: `OnboardingLogic#3`. */
data class StoreName(
    val logic: String,
    val instance: Int,
) {
    override fun toString(): String {
        return "$logic#$instance"
    }
}
