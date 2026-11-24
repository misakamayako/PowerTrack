plugins {
    alias(libs.plugins.powertrack.kotlin.jvm)
}

dependencies {
    // §4.3 允许的库：全部 KMP 库
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.collections.immutable)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
