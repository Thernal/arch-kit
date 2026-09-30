package io.thernal.archkit.detektrules

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import io.thernal.archkit.detektrules.collections.UnsafeCollectionIndexAccess
import io.thernal.archkit.detektrules.packageboundary.LayerPackageBoundary
import io.thernal.archkit.detektrules.packageboundary.LayerPackageRequired
import io.thernal.archkit.detektrules.preview.PreviewMustBePrivate
import io.thernal.archkit.detektrules.style.ExpressionBodyNotAllowed
import io.thernal.archkit.detektrules.style.MultilineConstructorRequired

class ProjectRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("project")

    override fun instance() = RuleSet(
        ruleSetId,
        listOf(
            ::PreviewMustBePrivate,
            ::UnsafeCollectionIndexAccess,
            ::LayerPackageBoundary,
            ::LayerPackageRequired,
            ::ExpressionBodyNotAllowed,
            ::MultilineConstructorRequired,
        ),
    )
}
