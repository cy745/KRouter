import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.publish)
}

group = libs.versions.krouter.group.get()
version = libs.versions.krouter.version.get()

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = group.toString(),
        artifactId = "core",
        version = version.toString()
    )

    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
            sourcesJar = true,
        )
    )
}
