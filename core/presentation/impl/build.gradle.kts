plugins {
    alias(libs.plugins.archkit.compose)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.domain)
                implementation(projects.core.event.api)
                implementation(projects.core.presentation.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.lifecycle.viewmodel.compose)
                // The one place the kit names Metro's ViewModel integration.
                implementation(libs.metrox.viewmodel)
                implementation(libs.metrox.viewmodel.compose)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.core.testing)
                implementation(projects.core.event.impl)
            }
        }
    }
}
