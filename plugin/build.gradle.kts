import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

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
    packageName("com.zhangke.krouter")

    buildConfigField("pluginGroup", libs.versions.krouter.group.get())
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
        create("plugin") {
            id = "${libs.versions.krouter.group.get()}.plugin"
            displayName = "plugin"
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

    pom {
        name = "KRouter Plugin"
        description = "plugin module of KRouter"
        inceptionYear = "2024"
        url = "https://github.com/cy745/KRouter/"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "cy745"
                name = "cy745"
                url = "https://github.com/cy745/"
            }
        }

        scm {
            url = "https://github.com/cy745/KRouter/"
            connection = "scm:git:git://github.com/cy745/KRouter.git"
            developerConnection = "scm:git:ssh://git@github.com/cy745/KRouter.git"
        }
    }

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}
