plugins {
    alias(libs.plugins.archkit.compose)
    alias(libs.plugins.archkit.injection)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.event.api)
                implementation(projects.core.presentation.api)
                implementation(projects.core.presentation.impl)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.metrox.viewmodel)
            }
        }
    }
}
