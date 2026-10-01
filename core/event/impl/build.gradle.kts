plugins {
    alias(libs.plugins.archkit.kmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.event.api)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
