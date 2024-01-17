@file:Suppress("UNUSED_VARIABLE", "UnstableApiUsage")

import com.google.protobuf.gradle.id
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

// define variables that get supplied through gradle.properties
val mavenRepositoryTokenType: String by project
val mavenRepositoryToken: String by project
val dokkaVersion: String by project
val protobufVersion: String by project
val grpcVersion: String by project
val grpcKotlinVersion: String by project
val log4jVersion: String by project
val slf4jVersion: String by project
val javaxAnnotationsVersion: String by project
val jsonSimpleVersion: String by project
val testContainersVersion: String by project
val mockkVersion: String by project
val pitestEngineVersion: String by project
val pitestJunitVersion: String by project
val coroutinesVersion: String by project
val junitVersion: String by project
val ktlintVersion: String by project

// provide general GAV coordinates
group = "net.justchunks"
version = "5.0.0-SNAPSHOT"
description = "Agones Client SDK (Kotlin/Java)"

// hook the plugins for the builds
plugins {
    `java-library`
    `maven-publish`
    idea
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.kotlinx.kover") version "0.7.4"
    id("org.jetbrains.dokka") version "1.9.10"
    id("org.sonarqube") version "4.4.1.3373"
    id("info.solidsoft.pitest") version "1.15.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("com.google.protobuf") version "0.9.4"
}

// configure the repositories for the dependencies
repositories {
    // official maven repository
    mavenCentral()
}

// declare all dependencies (for compilation and runtime)
dependencies {
    // add protobuf-java as a global api dependency (because of the generated messages)
    api("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    // add coroutines core (for flow and other techniques)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // add gRPC dependencies that are necessary for compilation and execution
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    runtimeOnly("io.grpc:grpc-netty:$grpcVersion")

    // classpaths we only compile against (are provided or unnecessary in runtime)
    compileOnly("org.slf4j:slf4j-api:$slf4jVersion")

    // testing resources (are present during compilation and runtime [shaded])
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("com.googlecode.json-simple:json-simple:$jsonSimpleVersion")
    testImplementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")

    // integrate the dokka html export plugin
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:$dokkaVersion")
}

// configure the java extension
java {
    // also generate javadoc and sources
    withSourcesJar()
}

// configure the kotlin extension
kotlin {
    // set the toolchain version that is required to build this project
    // replaces sourceCompatibility and targetCompatibility as it also sets these implicitly
    // https://kotlinlang.org/docs/gradle-configure-project.html#gradle-java-toolchains-support
    jvmToolchain(21)
}

// configure the protobuf extension (protoc + grpc)
protobuf {
    // configure the protobuf compiler for the proto compilation
    protoc {
        // set the artifact for protoc (the compiler version to use)
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }

    // configure the plugins for the protobuf build process
    plugins {
        // add a new "grpc" plugin for the java stub generation
        id("grpc") {
            // set the artifact for protobuf code generation (stubs)
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }

        // add a new "grpckt" plugin for the protobuf build process
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }

    // configure the proto tasks (extraction, generation, etc.)
    generateProtoTasks {
        // only modify the main source set, we don't have any proto files in test
        all().configureEach {
            // apply the "java" and "kotlin" builtin tasks as we are compiling against java and kotlin
            builtins {
                // id("java") – is added implicitly by default
                id("kotlin")
            }

            // apply the "grpc" and "grpckt" plugins whose specs are defined above, without special options
            plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}

// configure testing suites within gradle check phase
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(junitVersion)
        }
    }
}

// configure global tasks
val dokkaHtmlJar = tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("html-docs")
}

val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

// configure the publishing in the maven repository
publishing {
    // define the repositories that shall be used for publishing
    repositories {
        maven {
            url = uri("https://gitlab.scrayos.net/api/v4/projects/116/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = mavenRepositoryTokenType
                value = mavenRepositoryToken
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }

    // define the java components as publications for the repository
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// configure pitest plugin
pitest {
    // configure the most recent versions
    pitestVersion.set(pitestEngineVersion)
    junit5PluginVersion.set(pitestJunitVersion)

    // speed up performance by incremental, parallel builds
    threads.set(8)
    enableDefaultIncrementalAnalysis.set(true)

    // output results as xml and html
    outputFormats.addAll("XML", "HTML")
    timestampedReports.set(false)

    // add the individual source sets
    mainSourceSets.add(sourceSets.main)
    testSourceSets.add(sourceSets.test)
}

// configure ktlint
ktlint {
    // explicitly use a recent ktlint version for latest checks
    version = ktlintVersion

    // exclude any generated files
    filter {
        // exclude generated protobuf files
        exclude { element -> element.file.path.contains("/generated/") }
    }

    // configure the reporting to use checkstyle syntax
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
}

// configure sonarqube plugin
sonarqube {
    properties {
        property("sonar.projectName", "shard-format")
        property("sonar.projectVersion", version)
        property("sonar.projectDescription", description!!)
        property("sonar.pitest.mode", "reuseReport")
        property(
            "sonar.kotlin.ktlint.reportPaths",
            "build/reports/ktlint/ktlintKotlinScriptCheck/ktlintKotlinScriptCheck.xml," +
                "build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.xml," +
                "build/reports/ktlint/ktlintTestSourceSetCheck/ktlintTestSourceSetCheck.xml",
        )
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/kover/report.xml")
    }
}

// configure tasks
tasks {

    jar {
        // exclude the proto files as we won't need them in downstream projects
        exclude("**/*.proto")

        // exclude the now empty folders (because the proto files were removed)
        includeEmptyDirs = false

        // remove duplicates from the final jar
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    javadoc {
        // exclude the generated protobuf files
        exclude("agones/dev/sdk/**")
        exclude("grpc/gateway/protoc_gen_openapiv2/**")
    }
}
