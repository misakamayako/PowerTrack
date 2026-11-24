plugins {
    `kotlin-dsl`
}

group = "com.misakamayako.powertrack.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 用 implementation：约定插件里要 apply 这些插件，运行时必须有实现类
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "powertrack.android.application"
            implementationClass = "PowertrackAndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "powertrack.android.library"
            implementationClass = "PowertrackAndroidLibraryConventionPlugin"
        }
        register("kotlinJvm") {
            id = "powertrack.kotlin.jvm"
            implementationClass = "PowertrackKotlinJvmConventionPlugin"
        }
    }
}
