plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktech.publish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(libs.ksp.api)
    implementation("com.squareup:kotlinpoet:1.18.1")
    implementation("com.squareup:kotlinpoet-ksp:1.18.1")
}

group = libs.versions.krouter.group.get()
version = libs.versions.krouter.version.get()
