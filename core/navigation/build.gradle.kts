plugins {
    alias(libs.plugins.powertrack.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.misakamayako.powertrack.core.navigation"
}

dependencies {
    api(project(":core:ui"))
    api(project(":core:domain"))
    api(project(":core:model"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.navigation3.ui)
    api(libs.androidx.lifecycle.viewmodel.navigation3)
    api(libs.koin.core)
}
