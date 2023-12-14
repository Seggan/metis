plugins {
    kotlin("jvm") version "1.9.10" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "kotlin")
}

group = "io.github.seggan"
version = "0.3-SNAPSHOT"