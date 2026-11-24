plugins {
    alias(libs.plugins.powertrack.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.misakamayako.powertrack.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:domain"))
    api(project(":core:model"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)

    api(libs.mvikotlin)
    api(libs.mvikotlin.main)
    api(libs.mvikotlin.extensions.coroutines)
    api(libs.koin.core)
    api(libs.koin.compose)
    api(libs.kotlinx.collections.immutable)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
