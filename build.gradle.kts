@file:Suppress("UNUSED_VARIABLE")

import com.google.protobuf.gradle.*

// define variables that get supplied through gradle.properties
val mavenRepositoryTokenType: String by project
val mavenRepositoryToken: String by project

// provide general GAV coordinates
group = "net.justchunks"
version = "3.1.2-SNAPSHOT"
description = "Agones Java Client SDK"

// define the grpc versions for the build
val protobufVersion = "3.19.4"
val grpcVersion = "1.45.1"

// hook the plugins for the builds
plugins {
    `java-library`
    `maven-publish`
    jacoco
    idea
    id("org.sonarqube") version "3.3"
    id("info.solidsoft.pitest") version "1.7.0"
    id("com.google.protobuf") version "0.8.18"
}

// configure the repositories for the dependencies
repositories {
    // official maven repository
    mavenCentral()

    // justchunks repository
    maven {
        url = uri("https://gitlab.dev.scrayos.net/api/v4/groups/4/-/packages/maven")

        credentials(HttpHeaderCredentials::class) {
            name = mavenRepositoryTokenType
            value = mavenRepositoryToken
        }

        authentication {
            create<HttpHeaderAuthentication>("header")
        }
    }
}

// declare all dependencies (for compilation and runtime)
dependencies {
    // add the client base as a global api dependency (so that we can use it in interfaces)
    api("net.justchunks:client-base:1.2.0")

    // add protobuf-java as a global api dependency (because of the generated messages)
    api("com.google.protobuf:protobuf-java:$protobufVersion")

    // runtime resources (are present during compilation and runtime)
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    // https://github.com/lukas-krecan/future-converter
    // it was recommended against using ListenableFuture for new APIs: https://groups.google.com/g/guava-discuss/c/Relx9uIIpUg/m/1VT1ii9bCwAJ
    implementation("net.javacrumbs.future-converter:future-converter-java8-guava:1.2.0")

    // classpaths we only compile against (are provided or unnecessary in runtime)
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // testing resources (are present during compilation and runtime [shaded])
    testImplementation("org.mockito:mockito-junit-jupiter:4.3.1")
    testImplementation("org.testcontainers:testcontainers:1.16.3")
    testImplementation("org.testcontainers:junit-jupiter:1.16.3")
    testImplementation("com.googlecode.json-simple:json-simple:1.1.1")
    testImplementation("org.apache.logging.log4j:log4j-core:2.17.1")
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.1")

    // classpath we only compile our test-code against (are provided or unnecessary in runtime)
    testCompileOnly("org.jetbrains:annotations:23.0.0")
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
    }

    // configure the proto tasks (extraction, generation, etc.)
    generateProtoTasks {
        // only modify the main source set, we don't have any proto files in test
        ofSourceSet("main").forEach {
            // apply the "grpc" plugin whose spec is defined above, without special options
            it.plugins {
                id("grpc")
            }
        }
    }
}

// configure testing suites within gradle check phase
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.8.2")
        }
    }
}

// configure the publishing in the maven repository
publishing {
    // define the repositories that shall be used for publishing
    repositories {
        maven {
            url = uri("https://gitlab.dev.scrayos.net/api/v4/projects/116/packages/maven")
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
    pitestVersion.set("1.7.3")
    junit5PluginVersion.set("0.15")

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
        property("sonar.projectName", "AgonesClientSDK")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

// configure tasks
tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:all")
        options.compilerArgs.add("-Xlint:-processing")
        options.encoding = "UTF-8"
    }

    jar {
        // exclude the proto files as we won't need them in downstream projects
        exclude("**/*.proto")

        // exclude the now empty folders (because the proto files were removed)
        includeEmptyDirs = false
    }

    withType<Javadoc> {
        // we always want a javadoc, so we fail if javadoc fails
        isFailOnError = true

        options {
            breakIterator(false)
            charset("UTF-8")
            encoding("UTF-8")
            quiet()

            title = "${project.name} ${project.version} API"
            locale = "de_DE"
            windowTitle = "${project.name} ${project.version} API"
            overview = "${projectDir}/src/main/overview.html"
        }

        (options as StandardJavadocDocletOptions).addStringOption("'Xdoclint:all'")

        (options as StandardJavadocDocletOptions).tags = listOf(
            "apiNote:a:API Note",
            "implSpec:a:Implementation Requirements",
            "implNote:a:Implementation Note",
            "requirement:a:Platform Requirement"
        )
        (options as StandardJavadocDocletOptions).serialWarn(true)
        (options as StandardJavadocDocletOptions).author(false)
    }

    jacocoTestReport {
        reports {
            xml.required.set(true)
            csv.required.set(true)
        }

        // include multiple jacoco exec files (we separate unit and integration tests)
        executionData.setFrom(fileTree(buildDir).include("/jacoco/*.exec"))
    }

    // register a new task to fetch the version for the release script
    register("getVersion") {
        doLast {
            println(version)
        }
    }
}
