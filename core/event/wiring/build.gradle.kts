plugins {
    alias(libs.plugins.archkit.kmp.library)
    alias(libs.plugins.archkit.injection)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.event.api)
                implementation(projects.core.event.impl)
            }
        }
    }
}
