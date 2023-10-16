plugins {
    kotlin("jvm") version "1.9.0" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "kotlin")
}

group = "io.github.seggan"
version = "1.0-SNAPSHOT"