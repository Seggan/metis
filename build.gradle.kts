plugins {
    application
    kotlin("jvm") version "1.9.0"
}

group = "io.github.seggan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.github.seggan.slimelang.MainKt")
}