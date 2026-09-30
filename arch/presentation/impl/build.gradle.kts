plugins {
    alias(libs.plugins.archkit.compose)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.arch.domain)
                implementation(projects.arch.event.api)
                implementation(projects.arch.presentation.api)
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
                implementation(projects.arch.testing)
                implementation(projects.arch.event.impl)
            }
        }
    }
}
