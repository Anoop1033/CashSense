plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    // From Kotlin 2.0 the Compose compiler ships as its own plugin, versioned in lockstep with
    // Kotlin, instead of the `composeOptions.kotlinCompilerExtensionVersion` the project used
    // under Kotlin 1.9.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}
