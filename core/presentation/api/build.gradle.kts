plugins {
    alias(libs.plugins.archkit.compose)
}

kotlin {
    sourceSets {
        commonMain {
            // Nothing is re-exported: a feature module declares lifecycle, coroutines, Compose
            // resources and core/domain itself when its own code uses their types
            // (core/presentation/api/README.md → Dependencies you declare).
            dependencies {
                implementation(projects.core.domain)
                implementation(projects.core.event.api)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.compose.components.resources)
            }
        }
    }
}
