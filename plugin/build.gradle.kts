import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("kotlin")
    id("java-gradle-plugin")
    alias(libs.plugins.build.config)
    alias(libs.plugins.vanniktech.publish)
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
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin.api)

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

mavenPublishing {
    coordinates(
        groupId = group.toString(),
        artifactId = "plugin",
        version = version.toString()
    )

    configure(
        GradlePlugin(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = true,
        )
    )
}
