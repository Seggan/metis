plugins {
    id("org.jetbrains.dokka") version "1.9.10"
}

tasks.dokkaHtml {
    moduleName.set("metis-lang")
    outputDirectory.set(rootDir.resolve("gendocs/javadocs"))
}