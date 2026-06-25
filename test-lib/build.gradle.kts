plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
        }
    }
}

ksp {
    arg("kRouterType", "collect")
}

dependencies {
    add("kspJvm", project(":compiler"))
}
