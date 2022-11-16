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

import io.spine.internal.dependency.Spine
import io.spine.internal.gradle.standardToSpineSdk
import org.gradle.api.file.SourceDirectorySet

buildscript {
    // Apply the script created by `io.spine.tools.gradle.testing.TestEnvGradle`.
    //
    // The script defines `enclosingRootDir` variable that we use below.
    //
    apply(from = "$rootDir/test-env.gradle")
    val enclosingRootDir: String by extra

    apply(from = "${enclosingRootDir}/version.gradle.kts")
    val versionToPublish: String by extra

    standardSpineSdkRepositories()

    val spine = io.spine.internal.dependency.Spine(rootProject)
    dependencies {
        classpath(io.spine.internal.dependency.Protobuf.GradlePlugin.lib)
        classpath(spine.mcJavaPlugin)
        classpath("io.spine.tools:spine-model-check-bundle:${versionToPublish}")
    }

    io.spine.internal.gradle.doForceVersions(configurations)
}

plugins {
    java
}

apply(from = "$rootDir/test-env.gradle")
val enclosingRootDir: String by extra

apply(from = "$enclosingRootDir/version.gradle.kts")
val versionToPublish: String by extra

apply {
    plugin("com.google.protobuf")
    plugin("io.spine.mc-java")
    plugin("io.spine.tools.spine-model-check")
}

repositories {
    standardToSpineSdk()
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf(
        "-processor",
        "io.spine.model.assemble.AssignLookup",
        "-AspineDirRoot=${rootDir}"
    ))
}

dependencies {
    val spine = Spine(project)
    implementation(spine.validation.runtime)
    implementation(spine.server)
    implementation(spine.baseTypes)
    annotationProcessor("io.spine.tools:spine-model-check-bundle:$versionToPublish")
}

configurations.all {
    resolutionStrategy {
        force(
            /* Force the version of gRPC used by the `:client` module over the one
               set by `mc-java` in the `:core` module when specifying compiler artifact
               for the gRPC plugin.
               See `io.spine.tools.mc.java.gradle.plugins.JavaProtocConfigurationPlugin
               .configureProtocPlugins() method which sets the version from resources. */
            "io.grpc:protoc-gen-grpc-java:${io.spine.internal.dependency.Grpc.version}",
        )
    }
}

sourceSets {
    main {
        java.srcDirs("$projectDir/generated/main/java", "$projectDir/generated/main/spine")
        resources.srcDirs("$projectDir/generated/main/resources")
        (extensions["proto"] as SourceDirectorySet).srcDirs("$projectDir/src/main/proto")
    }
}
