plugins {
    kotlin("jvm") version "2.3.0" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.apply("kotlin")
}

group = "io.github.seggan"
version = "0.4-SNAPSHOT"