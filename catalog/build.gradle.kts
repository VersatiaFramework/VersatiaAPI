plugins {
    `version-catalog`
    `maven-publish`
}

group = "dev.versatia"
val versatiaVersion = providers.gradleProperty("versatiaVersion").get()
version = versatiaVersion

// The shared third-party versions plus the Versatia artifacts at this build's version; see docs/build.md
catalog {
    versionCatalog {
        from(files("../gradle/libs.versions.toml"))
        version("versatia", versatiaVersion)
        library("versatia-api", "dev.versatia", "VersatiaAPI").versionRef("versatia")
        library("versatia-ksp", "dev.versatia", "VersatiaAPI-ksp").versionRef("versatia")
        library("versatia-starter", "dev.versatia", "VersatiaStarter").versionRef("versatia")
        library("versatia-database", "dev.versatia", "VersatiaModule-Database").versionRef("versatia")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "VersatiaCatalog"
            from(components["versionCatalog"])
        }
    }
}
