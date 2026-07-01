pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
rootProject.name = "KRoute"
include(":compiler")
include(":core")
include(":plugin")
include(":test-app")
include(":test-lib")
include(":ksp-collector")
