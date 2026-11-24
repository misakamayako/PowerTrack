plugins {
    alias(libs.plugins.powertrack.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.misakamayako.powertrack.feature.history"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(libs.koin.compose)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
