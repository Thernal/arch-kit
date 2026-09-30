plugins {
    alias(libs.plugins.archkit.kmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.arch.event.api)
                implementation(projects.arch.event.impl)
                implementation(projects.arch.presentation.api)
                implementation(projects.arch.presentation.impl)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.lifecycle.viewmodel)
            }
        }
    }
}
