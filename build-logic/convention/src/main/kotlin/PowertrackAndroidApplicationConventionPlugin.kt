import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * `:app` 专用约定插件。
 */
class PowertrackAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<ApplicationExtension> {
                compileSdk = ProjectConfig.COMPILE_SDK
                defaultConfig {
                    minSdk = ProjectConfig.MIN_SDK
                    targetSdk = ProjectConfig.TARGET_SDK
                    testInstrumentationRunner = ProjectConfig.TEST_INSTRUMENTATION_RUNNER
                    vectorDrawables.useSupportLibrary = true
                }
                compileOptions {
                    sourceCompatibility = ProjectConfig.JAVA_VERSION_ENUM
                    targetCompatibility = ProjectConfig.JAVA_VERSION_ENUM
                    isCoreLibraryDesugaringEnabled = true
                }
                buildFeatures {
                    buildConfig = true
                }
                lint {
                    abortOnError = false
                }
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                        excludes += "/META-INF/LICENSE*"
                        excludes += "META-INF/*.kotlin_module"
                    }
                }
                testOptions {
                    unitTests.isReturnDefaultValues = true
                }
            }

            // 同 library 约定插件：等 Compose 插件真正应用后再开 buildFeatures.compose，
            // 避免 AGP 报 INCONSISTENT_BUILD_FEATURE_SETTING。
            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
                extensions.configure<ApplicationExtension> {
                    buildFeatures.compose = true
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }

            dependencies {
                add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.5")
            }
        }
    }
}
