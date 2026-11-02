import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "com.github.marcoral.versatia"
version = providers.gradleProperty("versatiaVersion").get()

base {
    archivesName = "VersatiaAPI"
}

// Exported to consumers' compile classpath; see docs/build.md
dependencies {
    compileOnlyApi(libs.paper.api)
    api(kotlin("stdlib"))
    api(kotlin("reflect"))
    // Exported like kotlin-stdlib and pinned: plugins run against VersatiaCore's copy, and inline functions compile references to its internals; see docs/build.md
    api(libs.kotlinx.coroutines.core) { version { strictly(libs.versions.coroutines.get()) } }
    constraints {
        api(libs.kotlinx.coroutines.core.jvm) { version { strictly(libs.versions.coroutines.get()) } }
    }

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Paper 1.21.4 requires Java 21
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Framework code may use the plumbing marked @VersatiaInternalApi; see docs/api.md of VersatiaAPI
        optIn.add("com.github.marcoral.versatia.VersatiaInternalApi")
    }
}

tasks.test {
    useJUnitPlatform()
    // Read through ${env:VERSATIA_TEST_SECRET} by the tests of secrets
    environment("VERSATIA_TEST_SECRET", "from-the-environment")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "VersatiaAPI"
            from(components["java"])
        }
    }
}
