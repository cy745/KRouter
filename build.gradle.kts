import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dokka) apply false
}

// 将plugin和compiler发布到MavenLocal后，将下列代码取消注释，即可启用plugin
//buildscript {
//    val testGroup = libs.versions.krouter.group.get()
//    val testVersion = libs.versions.krouter.version.get()
//
//    dependencies { classpath("$testGroup:plugin:$testVersion") }
//}
//
//ext { set("targetInjectProjectName", "app") }
//apply(plugin = "krouter-plugin")

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
}