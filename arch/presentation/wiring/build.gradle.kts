plugins {
    alias(libs.plugins.archkit.compose)
    alias(libs.plugins.archkit.injection)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.arch.event.api)
                implementation(projects.arch.presentation.api)
                implementation(projects.arch.presentation.impl)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.metrox.viewmodel)
            }
        }
    }
}
