@file:Suppress("UNUSED_VARIABLE", "UnstableApiUsage")

import com.google.protobuf.gradle.id
import java.util.Locale as locale
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// define variables that get supplied through gradle.properties
val mavenRepositoryTokenType: String by project
val mavenRepositoryToken: String by project
val kotlinVersion: String by project
val protobufVersion: String by project
val grpcVersion: String by project
val grpcKotlinVersion: String by project
val log4jVersion: String by project
val jetbrainsAnnotationsVersion: String by project
val javaxAnnotationsVersion: String by project
val jsonSimpleVersion: String by project
val testContainersVersion: String by project
val mockkVersion: String by project
val pitestEngineVersion: String by project
val pitestJunitVersion: String by project
val coroutinesVersion: String by project

// provide general GAV coordinates
group = "net.justchunks"
version = "4.0.2-SNAPSHOT"
description = "Agones Client SDK (Kotlin/Java)"

// hook the plugins for the builds
plugins {
    `java-library`
    `maven-publish`
    idea
    kotlin("jvm") version "1.8.10"
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
    id("org.jetbrains.dokka") version "1.8.10"
    id("org.sonarqube") version "4.0.0.2929"
    id("info.solidsoft.pitest") version "1.9.11"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
    id("com.google.protobuf") version "0.9.3"
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
    compileOnly("org.apache.logging.log4j:log4j-api:$log4jVersion")

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
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:$kotlinVersion")
}

// configure the java extension (versions + jars)
java {
    // set the toolchain version that is required to build this project
    // replaces sourceCompatibility and targetCompatibility as it also sets these implicitly
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    // also generate javadoc and sources
    withSourcesJar()
    withJavadocJar()
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
            useJUnitJupiter("5.9.2")
        }
    }
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
    pitestVersion.set(pitestEngineVersion)
    junit5PluginVersion.set(pitestJunitVersion)

    threads.set(8)
    enableDefaultIncrementalAnalysis.set(true)

    outputFormats.addAll("XML", "HTML")
    timestampedReports.set(false)

    mainSourceSets.add(sourceSets.main)
    testSourceSets.add(sourceSets.test)
}

// configure sonarqube plugin
sonarqube {
    properties {
        property("sonar.projectName", "agones-client-sdk")
        property("sonar.projectVersion", version)
        property("sonar.projectDescription", description!!)
        property("sonar.pitest.mode", "reuseReport")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/kover/xml/report.xml")
    }
}

// configure ktlint plugin
ktlint {
    filter {
        // exclude generated protobuf files
        exclude { element -> element.file.path.contains("/generated/") }
    }
}

// configure tasks
tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:all")
        options.compilerArgs.add("-Xlint:-processing")
        options.encoding = "UTF-8"
    }

    // this is necessary so that intelliJ does not reset the version
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

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

    // register a new task to fetch the version for the release script
    register("getVersion") {
        doLast {
            println(version)
        }
    }

    // register a new task to print the coverage for gitlab
    register("koverReportPrint") {
        // invoke the report task first
        val koverReport = named("koverXmlReport")
        dependsOn(koverReport)

        // now print the calculated coverage
        doLast {
            //language=RegExp
            val regexp = """<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"/>""".toRegex()
            koverReport.get().outputs.files.forEach { file ->
                // Read file by lines
                file.useLines { lines ->
                    // Last line in file that matches regexp is the total coverage
                    lines.last(regexp::containsMatchIn).let { line ->
                        // Found the match
                        regexp.find(line)?.let {
                            val missed = it.groupValues[1].toDouble()
                            val covered = it.groupValues[2].toDouble()
                            val coverage = String.format(
                                locale.US,
                                "%.2f",
                                covered * 100 / (missed + covered)
                            )

                            println("Total Code Coverage: $coverage%")
                        }
                    }
                }
            }
        }
    }
}
