// ---------------------------------------------------------------------------
// 工具链组合护栏（§7.3：精确版本）
//
// 本项目锁定：Gradle 9.9.1 + AGP 9.4.1 + Kotlin 2.4.10（AGP 9 自带 Kotlin 支持）。
//
// 已知不兼容组合：AGP 8.x + Gradle 9.9+。Gradle 9.9 的 embedded Kotlin 2.4.21 把
// `org.jetbrains:annotations` **严格 pin 到 13.0**（reason: "Pinned to the embedded Kotlin"），
// 而 AGP 8.13.2 通过 ddmlib / layoutlib-api / repository 依赖 23.0.0，
// 于是 buildscript classpath 解析必然失败：
//   Could not resolve org.jetbrains:annotations:{strictly 13.0}
// 这个报错很隐晦，所以在配置阶段直接给出可执行的提示。
// ---------------------------------------------------------------------------
run {
    val current = GradleVersion.current()
    val catalogText = file("gradle/libs.versions.toml").takeIf { it.exists() }?.readText().orEmpty()
    val agpVersion = Regex("""(?m)^\s*agp\s*=\s*"([^"]+)"""")
        .find(catalogText)?.groupValues?.get(1).orEmpty()
    val agpMajor = agpVersion.substringBefore('.').toIntOrNull() ?: 9
    if (current >= GradleVersion.version("9.9") && agpMajor < 9) {
        throw GradleException(
            """
            检测到 AGP $agpVersion 配 Gradle ${current.version}：这是已知不兼容组合。
            Gradle 9.9+ 的 embedded Kotlin 把 org.jetbrains:annotations 严格锁定在 13.0，
            与 AGP 8.x 依赖的 23.0.0 冲突，buildscript classpath 无法解析。

            两个选择：
              1) 用仓库自带的 wrapper（推荐）：.\gradlew.bat / ./gradlew
                 当前 wrapper = Gradle ${GradleVersion.version("9.9.1").version} + AGP 9.4.1
              2) 若必须留在 AGP 8.x，把 Gradle 降到 8.14.x

            如果是 IDE 报的错：Settings → Build, Execution, Deployment → Build Tools → Gradle，
            把 "Use Gradle from" 改成 gradle-wrapper.properties。
            """.trimIndent(),
        )
    }
}

pluginManagement {
    includeBuild("build-logic")
    repositories {
        // 本机环境：dl.google.com / repo1.maven.org 直连不可用，优先走阿里云镜像
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "PowerTrack"

include(
    ":app",
    ":core:model",
    ":core:domain",
    ":core:data",
    ":core:designsystem",
    ":core:ui",
    ":core:navigation",
    ":feature:home",
    ":feature:exercise-library",
    ":feature:routine-editor",
    ":feature:workout",
    ":feature:history",
    ":feature:stats",
    ":feature:settings",
)
