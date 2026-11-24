import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * 所有 `core:*`（Android 侧）与 `feature:*` 模块共用的约定插件。
 */
class PowertrackAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<LibraryExtension> {
                compileSdk = ProjectConfig.COMPILE_SDK
                defaultConfig {
                    minSdk = ProjectConfig.MIN_SDK
                    testInstrumentationRunner = ProjectConfig.TEST_INSTRUMENTATION_RUNNER
                }
                compileOptions {
                    sourceCompatibility = ProjectConfig.JAVA_VERSION_ENUM
                    targetCompatibility = ProjectConfig.JAVA_VERSION_ENUM
                }
                buildFeatures {
                    buildConfig = true
                }
                lint {
                    abortOnError = false
                }
                testOptions {
                    unitTests.isReturnDefaultValues = true
                    unitTests.isIncludeAndroidResources = true
                }
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                        excludes += "META-INF/*.kotlin_module"
                    }
                }
            }

            // 不能在 apply 时用 hasPlugin 判断：那一刻 Compose 插件还没应用，会把 compose 设成 false，
            // 等 Compose 插件应用后 AGP 又自动打开它，于是报
            // INCONSISTENT_BUILD_FEATURE_SETTING（compose 插件已应用但 buildFeatures.compose=false）。
            // 用 withPlugin 等插件真正应用后再开，时序就无关了。
            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
                extensions.configure<LibraryExtension> {
                    buildFeatures.compose = true
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }
}
