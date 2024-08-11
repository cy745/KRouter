import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktech.publish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":core"))

    // 用于测试ksp处理器
    testImplementation("dev.zacsweers.kctfork:ksp:0.5.1")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    testImplementation(libs.junit)

    implementation(libs.ksp.api)
    implementation("com.squareup:kotlinpoet:1.18.1")
    implementation("com.squareup:kotlinpoet-ksp:1.18.1")
}

group = libs.versions.krouter.group.get()
version = libs.versions.krouter.version.get()

mavenPublishing {
    coordinates(
        groupId = group.toString(),
        artifactId = "compiler",
        version = version.toString()
    )

    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = true,
        )
    )
}
