import org.gradle.api.JavaVersion

/**
 * 全局常量：§0 定案速查表里的 SDK 版本集中在这里，任何模块都不许自己写数字。
 */
object ProjectConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    const val JAVA_VERSION = 17
    val JAVA_VERSION_ENUM: JavaVersion = JavaVersion.VERSION_17

    const val TEST_INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

    /** 包名前缀，全小写（§0 / D12） */
    const val NAMESPACE_PREFIX = "com.misakamayako.powertrack"
}
