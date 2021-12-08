/*
 * Copyright 2021, TeamDev. All rights reserved.
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

import io.spine.internal.gradle.applyGitHubPackages
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

    io.spine.internal.gradle.doApplyGitHubPackages(repositories, "core-java", rootProject)
    io.spine.internal.gradle.doApplyStandard(repositories)

    val spineBaseVersion: String by extra
    val spineTimeVersion: String by extra
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
                "io.spine:spine-base:$spineBaseVersion",
                "io.spine:spine-time:$spineTimeVersion",
                "io.spine.tools:spine-plugin-base:$toolBaseVersion"
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
val spineBaseTypesVersion: String by extra
val versionToPublish: String by extra

val scriptsPath = io.spine.internal.gradle.Scripts.commonPath
apply {
    plugin("com.google.protobuf")
    plugin("io.spine.mc-java")
    plugin("io.spine.tools.spine-model-verifier")
    from("$enclosingRootDir/version.gradle.kts")
}

repositories.applyGitHubPackages("core-java", rootProject)
repositories.applyStandard()

tasks.compileJava {
    options.compilerArgs.addAll(listOf(
        "-processor",
        "io.spine.model.assemble.AssignLookup",
        "-AspineDirRoot=${rootDir}"
    ))
}

dependencies {
    implementation("io.spine:spine-server:$versionToPublish")
    implementation("io.spine:spine-base-types:$spineBaseTypesVersion")
    annotationProcessor("io.spine.tools:spine-model-assembler:$versionToPublish")
}

sourceSets {
    main {
        java.srcDirs("$projectDir/generated/main/java", "$projectDir/generated/main/spine")
        resources.srcDirs("$projectDir/generated/main/resources")
        (extensions["proto"] as SourceDirectorySet).srcDirs("$projectDir/src/main/proto")
    }
}
