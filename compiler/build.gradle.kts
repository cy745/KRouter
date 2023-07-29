plugins {
    id("java")
    kotlin("jvm")
    id("maven-publish")
}

group = "com.zhangke.krouter"
version = "0.1.3"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":core"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.8.0-1.0.9")
    implementation("com.squareup:kotlinpoet:1.12.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "compiler"
            from(components["java"])
        }
    }
}
