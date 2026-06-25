import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ktlint) apply false
}

// 将 plugin 和 compiler 发布到 MavenLocal 后，将下列代码取消注释，即可启用 plugin
// buildscript {
//     val testGroup = libs.versions.krouter.group.get()
//     val testVersion = libs.versions.krouter.version.get()
//
//     dependencies { classpath("$testGroup:plugin:$testVersion") }
// }
//
// ext { set("targetInjectProjectName", "app") }
// apply(plugin = "krouter-plugin")

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.4.0")
        verbose.set(true)
        android.set(false)
        ignoreFailures.set(true)
        filter {
            exclude { element ->
                element.file.path.contains("generated") ||
                    (element.file.name == "build.gradle.kts" && element.file.parentFile == rootProject.projectDir)
            }
        }
    }
}
