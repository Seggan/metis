plugins {
    id("org.jetbrains.dokka") version "1.9.10"
    `maven-publish`
}

tasks.dokkaHtml {
    moduleName.set("metis-lang")
    outputDirectory.set(rootDir.resolve("gendocs/javadocs"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.seggan"
            artifactId = "metis-lang"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
}