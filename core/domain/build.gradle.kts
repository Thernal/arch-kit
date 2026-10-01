plugins {
    alias(libs.plugins.archkit.kmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            // Flow and CoroutineScope appear in these signatures and are not re-exported: a consumer
            // declares coroutines itself.
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
