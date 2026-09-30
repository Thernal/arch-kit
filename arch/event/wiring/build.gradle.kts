plugins {
    alias(libs.plugins.archkit.kmp.library)
    alias(libs.plugins.archkit.injection)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.arch.event.api)
                implementation(projects.arch.event.impl)
            }
        }
    }
}
