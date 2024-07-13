plugins {
    kotlin("jvm") version "2.0.0" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "kotlin")
}

group = "io.github.seggan"
version = "0.4-SNAPSHOT"