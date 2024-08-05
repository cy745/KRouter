plugins {
    id("java")
    kotlin("jvm")
    id("java-gradle-plugin")
    id("maven-publish")
    alias(libs.plugins.build.config)
    kotlin("kapt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

buildConfig {
    buildConfigField("pluginVersion", libs.versions.krouter.version.get())
}

dependencies {
    implementation(gradleApi())
    implementation(libs.ksp.gradle)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.auto.service)
    kapt(libs.auto.service)

    // 用于测试kcp处理器
    testImplementation("dev.zacsweers.kctfork:core:0.5.1")
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.junit)

    implementation(project(":core"))
}

gradlePlugin {
    plugins {
        create("krouter-plugin") {
            id = "krouter-plugin"
            implementationClass = "com.zhangke.krouter.plugin.RouterPlugin"
        }
    }
}

group = libs.versions.krouter.group.get()
version = libs.versions.krouter.version.get()

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "plugin"
            from(components["java"])
        }
    }
}
