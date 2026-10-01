plugins {
    alias(libs.plugins.archkit.kmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.event.api)
                implementation(projects.core.event.impl)
                implementation(projects.core.presentation.api)
                implementation(projects.core.presentation.impl)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.lifecycle.viewmodel)
            }
        }
    }
}
