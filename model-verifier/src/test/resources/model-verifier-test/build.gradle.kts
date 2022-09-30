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

import io.spine.internal.gradle.applyStandard
import org.gradle.api.file.SourceDirectorySet

buildscript {
    // Apply the script created by `io.spine.tools.gradle.testing.TestEnvGradle`.
    //
    // The script defines `enclosingRootDir` variable that we use below.
    //
    apply(from = "$rootDir/test-env.gradle")

    // Applying from `version.gradle.kts` inside the `buildscript` section to reuse the properties.
    //
    // As long as `buildscript` section is always evaluated first, we need to apply
    // `version.gradle.kts` explicitly here.
    //
    val enclosingRootDir: String by extra
    apply(from = "${enclosingRootDir}/version.gradle.kts")

    io.spine.internal.gradle.doApplyStandard(repositories)

    val baseVersion: String by extra
    val coreJavaVersion: String by extra
    val timeVersion: String by extra
    val toolBaseVersion: String by extra
    val mcJavaVersion: String by extra
    val versionToPublish: String by extra

    io.spine.internal.gradle.doForceVersions(configurations)
    dependencies {
        classpath(io.spine.internal.dependency.Protobuf.GradlePlugin.lib)
        classpath("io.spine.tools:spine-mc-java:${mcJavaVersion}")
        classpath("io.spine.tools:spine-model-verifier:${versionToPublish}")
    }

    configurations.all {
        resolutionStrategy {
            force(
                "io.spine:spine-base:$baseVersion",
                "io.spine:spine-validate:$baseVersion",
                "io.spine:spine-time:$timeVersion",
                "io.spine:spine-server:$coreJavaVersion",
                "io.spine.tools:spine-tool-base:$toolBaseVersion",
                "io.spine.tools:spine-plugin-base:$toolBaseVersion",
            )
        }
    }
}

plugins {
    java
}

apply(from = "$rootDir/test-env.gradle")
val enclosingRootDir: String by extra

apply(from = "$enclosingRootDir/version.gradle.kts")
val baseVersion: String by extra
val baseTypesVersion: String by extra
val coreJavaVersion: String by extra
val toolBaseVersion: String by extra
val versionToPublish: String by extra

val scriptsPath = io.spine.internal.gradle.Scripts.commonPath
apply {
    plugin("com.google.protobuf")
    plugin("io.spine.mc-java")
    plugin("io.spine.tools.spine-model-verifier")
    from("$enclosingRootDir/version.gradle.kts")
}

repositories.applyStandard()

tasks.compileJava {
    options.compilerArgs.addAll(listOf(
        "-processor",
        "io.spine.model.assemble.AssignLookup",
        "-AspineDirRoot=${rootDir}"
    ))
}

dependencies {
    implementation("io.spine:spine-validate:$baseVersion")
    implementation("io.spine:spine-server:$coreJavaVersion")
    implementation("io.spine:spine-base-types:$baseTypesVersion")
    annotationProcessor("io.spine.tools:spine-model-assembler:$versionToPublish")
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
