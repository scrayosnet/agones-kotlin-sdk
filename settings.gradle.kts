pluginManagement {
    repositories {
        // maven central rate-limits the shared ips of renovate, so plugins come from google's mirror of it first
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "agones-client-sdk"

buildCache {
    local {
        directory = File(rootDir, "build-cache")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
