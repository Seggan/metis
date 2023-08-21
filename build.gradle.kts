import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0" apply false
}

group = "io.github.seggan"
version = "1.0-SNAPSHOT"

subprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "kotlin")

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}