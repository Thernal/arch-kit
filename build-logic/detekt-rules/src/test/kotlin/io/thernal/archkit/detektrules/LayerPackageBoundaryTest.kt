package io.thernal.archkit.detektrules

import dev.detekt.api.Config
import dev.detekt.test.lint
import io.thernal.archkit.detektrules.packageboundary.LayerPackageBoundary
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerPackageBoundaryTest {
    private val rule = LayerPackageBoundary(Config.empty)

    @Test
    fun `reports data imports from presentation and allows domain`() {
        val findings = rule.lint(
            """
            package io.thernal.archkit.arch.impl.data

            import io.thernal.archkit.arch.impl.domain.deeplink.DeepLinkParser
            import io.thernal.archkit.arch.impl.presentation.host.NavigationView
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports presentation imports from data and allows domain`() {
        val findings = rule.lint(
            """
            package io.thernal.archkit.arch.impl.presentation.host

            import io.thernal.archkit.arch.impl.data.RuntimeDeepLinkBridge
            import io.thernal.archkit.arch.impl.domain.navigator.BackStackNavigator
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports domain imports from data and presentation`() {
        val findings = rule.lint(
            """
            package io.thernal.archkit.arch.impl.domain.navigator

            import io.thernal.archkit.arch.api.presentation.model.Route
            import io.thernal.archkit.arch.impl.data.RuntimeDeepLinkBridge
            import io.thernal.archkit.arch.impl.presentation.host.NavigationView
            """.trimIndent(),
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `allows presentation to name a domain type inside an api module`() {
        val findings = rule.lint(
            """
            package io.thernal.archkit.arch.api.presentation.deeplink

            import io.thernal.archkit.arch.api.domain.DeepLinkSource
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `ignores the api module of the same capability`() {
        val findings = rule.lint(
            """
            package io.thernal.archkit.arch.impl.domain.deeplink

            import io.thernal.archkit.arch.api.data.DeepLinkService
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `ignores another module and non layered packages`() {
        val findings = rule.lint(
            """
            package io.thernal.archkit.arch.impl.data

            import io.thernal.archkit.session.impl.presentation.SessionState
            import io.thernal.archkit.arch.wiring.NavigationWiring
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }
}
