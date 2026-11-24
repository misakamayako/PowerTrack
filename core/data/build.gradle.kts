plugins {
    alias(libs.plugins.powertrack.android.library)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.misakamayako.powertrack.data"
    // seed JSON 从 assets 读取
    androidResources {
        noCompress += "json"
    }
}

sqldelight {
    databases {
        create("PowerTrackDatabase") {
            packageName.set("com.misakamayako.powertrack.db")
            // §3.7：minSdk 26 对应系统 SQLite 3.18，dialect 必须锁 3.18
            dialect("app.cash.sqldelight:sqlite-3-18-dialect:${libs.versions.sqldelight.get()}")
            verifyMigrations.set(false)
            generateAsync.set(false)
        }
    }
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:model"))

    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
