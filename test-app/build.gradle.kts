plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":test-lib"))
        }

        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(project(":test-lib"))
        }

        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

ksp {
    arg("kRouterType", "inject")
}

dependencies {
    add("kspJvm", project(":compiler"))
}
