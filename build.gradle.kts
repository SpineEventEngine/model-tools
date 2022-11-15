/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import io.spine.internal.dependency.ErrorProne
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.JUnit
import io.spine.internal.dependency.Spine
import io.spine.internal.gradle.publish.IncrementGuard
import io.spine.internal.gradle.VersionWriter
import io.spine.internal.gradle.checkstyle.CheckStyleConfig
import io.spine.internal.gradle.excludeProtobufLite
import io.spine.internal.gradle.forceVersions
import io.spine.internal.gradle.github.pages.updateGitHubPages
import io.spine.internal.gradle.javac.configureErrorProne
import io.spine.internal.gradle.javac.configureJavac
import io.spine.internal.gradle.javadoc.JavadocConfig
import io.spine.internal.gradle.kotlin.applyJvmToolchain
import io.spine.internal.gradle.kotlin.setFreeCompilerArgs
import io.spine.internal.gradle.publish.PublishingRepos
import io.spine.internal.gradle.publish.spinePublishing
import io.spine.internal.gradle.report.coverage.JacocoConfig
import io.spine.internal.gradle.report.license.LicenseReporter
import io.spine.internal.gradle.report.pom.PomGenerator
import io.spine.internal.gradle.standardToSpineSdk
import io.spine.internal.gradle.testing.configureLogging
import io.spine.internal.gradle.testing.registerTestTasks
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    standardSpineSdkRepositories()

    dependencies {
        classpath(io.spine.internal.dependency.Spine.McJava.pluginLib)
    }

    io.spine.internal.gradle.doForceVersions(configurations)
}

plugins {
    `java-library`
    kotlin("jvm")
    idea
    protobuf
    errorprone
}

spinePublishing {
    modules = setOf(
        // We only publish one module because it produces a fat JAR publication.
        "model-check"
    )

    destinations = with(PublishingRepos) {
        setOf(
            cloudRepo,
            gitHub("model-tools"),
            cloudArtifactRegistry
        )
    }

    dokkaJar {
        enabled = true
    }
}

allprojects {
    apply {
        plugin("jacoco")
        plugin("idea")
        plugin("project-report")
    }

    apply {
        from("$rootDir/version.gradle.kts")
    }

    group = "io.spine.tools"
    version = extra["versionToPublish"]!!

    repositories {
        standardToSpineSdk()
    }
}

subprojects {
    apply {
        plugin("java-library")
        plugin("jacoco")
        plugin("com.google.protobuf")
        plugin("net.ltgt.errorprone")
        plugin("io.spine.mc-java")
        plugin("kotlin")
        plugin("pmd")
        plugin("maven-publish")
        plugin("pmd-settings")
        plugin("dokka-for-java")
    }

    java {
        tasks {
            withType<JavaCompile>().configureEach {
                configureJavac()
                configureErrorProne()
            }
            withType<org.gradle.jvm.tasks.Jar>().configureEach {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        }
    }

    kotlin {
        val javaVersion = JavaVersion.VERSION_11.toString()

        applyJvmToolchain(javaVersion)
        explicitApi()

        tasks.withType<KotlinCompile>().configureEach {
            kotlinOptions.jvmTarget = javaVersion
            setFreeCompilerArgs()
        }
    }

    val spine = Spine(project)

    dependencies {
        ErrorProne.apply {
            errorprone(core)
        }

        api(spine.base)

        testImplementation(JUnit.runner)
        testImplementation(spine.testlib)
    }

    configurations {
        forceVersions()
        excludeProtobufLite()

        all {
            resolutionStrategy {
                force(
                    /* Force the version of gRPC used by the `:client` module over the one
                       set by `mc-java` in the `:core` module when specifying compiler artifact
                       for the gRPC plugin.
                       See `io.spine.tools.mc.java.gradle.plugins.JavaProtocConfigurationPlugin
                       .configureProtocPlugins() method which sets the version from resources. */
                    "io.grpc:protoc-gen-grpc-java:${Grpc.version}",

                    spine.base,
                    spine.validate,
                    spine.testlib,

                    Grpc.core,
                    Grpc.protobuf,
                    Grpc.stub
                )
            }
        }
    }

    val generatedDir = "$projectDir/generated"
    val generatedJavaDir = "$generatedDir/main/java"
    val generatedTestJavaDir = "$generatedDir/test/java"
    val generatedGrpcDir = "$generatedDir/main/grpc"
    val generatedTestGrpcDir = "$generatedDir/test/grpc"
    val generatedSpineDir = "$generatedDir/main/spine"
    val generatedTestSpineDir = "$generatedDir/test/spine"

    sourceSets {
        main {
            java.srcDirs(
                generatedSpineDir,
                generatedJavaDir,
            )
        }
        test {
            java.srcDirs(
                generatedTestSpineDir,
                generatedTestJavaDir,
            )
        }
    }

    tasks {
        val generateRejections by existing
        compileKotlin {
            dependsOn(generateRejections)
        }

        val generateTestRejections by existing
        compileTestKotlin {
            dependsOn(generateTestRejections)
        }

        registerTestTasks()
        test {
            useJUnitPlatform { includeEngines("junit-jupiter") }
            configureLogging()
        }
    }

    apply<IncrementGuard>()
    apply<VersionWriter>()

    LicenseReporter.generateReportIn(project)
    JavadocConfig.applyTo(project)
    CheckStyleConfig.applyTo(project)

    idea {
        module {
            generatedSourceDirs.addAll(
                files(
                    generatedJavaDir,
                    generatedGrpcDir,
                    generatedSpineDir,
                    generatedTestJavaDir,
                    generatedTestGrpcDir,
                    generatedTestSpineDir
                )
            )

            testSources.from(generatedTestJavaDir)

            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }

    /**
     * Determines whether this project should expose its Javadoc to
     * `SpineEventEngine.github.io`website.
     *
     * Currently, the `testutil` projects are excluded from publishing, as well as the modules
     * that perform the model compile-time checks.
     *
     * @return `true` is the project Javadoc should be published, `false` otherwise
     */
    fun shouldPublishJavadoc() =
        !project.name.startsWith("testutil") &&
                !project.name.startsWith("model")

    updateGitHubPages(project.version.toString()) {
        allowInternalJavadoc.set(true)
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("${project.path}:updateGitHubPages")
    }
}

JacocoConfig.applyTo(project)
PomGenerator.applyTo(project)
LicenseReporter.mergeAllReports(project)
