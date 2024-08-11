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

val isInJitPack = System.getenv()["JITPACK"] == "true"
if (!isInJitPack) {
    include(":sample:profile")
    include(":sample:home")
    include(":sample:setting")
    include(":sample:sample-core")
    include(":sample:app")
}
