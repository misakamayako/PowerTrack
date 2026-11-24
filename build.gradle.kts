// 根构建脚本：只负责声明插件版本，不 apply（§4.2 app 是唯一聚合点）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.powertrack.android.application) apply false
    alias(libs.plugins.powertrack.android.library) apply false
    alias(libs.plugins.powertrack.kotlin.jvm) apply false
}
