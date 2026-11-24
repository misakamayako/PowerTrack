import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.withType

/**
 * `core:model` / `core:domain` 用：纯 `kotlin("jvm")` 模块 + **零 Android import** 强制检查（§4.3 / D9）。
 */
class PowertrackKotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<JavaPluginExtension> {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(ProjectConfig.JAVA_VERSION))
                }
            }

            val guard = tasks.register<AndroidImportGuardTask>("checkNoAndroidImports") {
                group = "verification"
                description =
                    "KMP-ready 硬标准：core:model / core:domain 源码禁止出现 import android.* / androidx.*"
                sources.from(fileTree("src") { include("**/*.kt") })
                moduleName.set(project.name)
                rootDir.set(project.projectDir)
            }

            tasks.withType<Task>().matching { it.name == "check" || it.name == "build" }.configureEach {
                dependsOn(guard)
            }
        }
    }
}

abstract class AndroidImportGuardTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>

    /** 只用于把路径打印短一点，不参与输入快照。 */
    @get:Internal
    abstract val rootDir: org.gradle.api.file.DirectoryProperty

    @TaskAction
    fun check() {
        val forbidden = Regex("""^\s*import\s+(android|androidx)\.""")
        val violations = mutableListOf<String>()
        val root = rootDir.get().asFile
        sources.files
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (forbidden.containsMatchIn(line)) {
                        violations += "${file.relativeTo(root).path}:${index + 1}: ${line.trim()}"
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "模块 ${moduleName.get()} 必须是 KMP-ready 的纯 Kotlin 模块，" +
                    "禁止 import android.* / androidx.*：\n" + violations.joinToString("\n"),
            )
        }
    }
}
