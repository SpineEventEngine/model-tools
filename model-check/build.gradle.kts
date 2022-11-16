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
import io.spine.internal.gradle.publish.SpinePublishing

plugins {
    `maven-publish`
    `detekt-code-analysis`
    id("com.github.johnrengelman.shadow").version("7.1.2")
}

dependencies {
    shadow(localGroovy())
    shadow(gradleApi())

    val spine = Spine(project)

    implementation(spine.server)
    implementation(spine.pluginBase)

    implementation(project(":model-assembler"))

    testImplementation(gradleTestKit())
    testImplementation(spine.testlib)
    testImplementation(spine.pluginTestlib)
    testImplementation(spine.coreJava.testUtilServer)
}

tasks.test {
    dependsOn("publishToMavenLocal",
              ":model-check:publishToMavenLocal")
}

/** The publishing settings from the root project. */
val spinePublishing = rootProject.the<SpinePublishing>()

/**
 * The ID of the far JAR artifact.
 *
 * This value is also used in `io.spine.tools.mc.java.gradle.Artifacts.kt`.
 */
val pArtifact = spinePublishing.artifactPrefix + "model-check-bundle"

publishing {
    publications {
        create("fatJar", MavenPublication::class) {
            artifactId = pArtifact
            artifact(tasks.shadowJar)
        }
    }
}

/**
 * Avoiding Gradle warning on disabling execution optimization because of
 * missing explicit dependency.
 */
val publishFatJarPublicationToMavenLocal: Task by tasks.getting {
    dependsOn(tasks.jar)
}

tasks.publish {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    exclude(
        /**
         * Exclude Gradle types to reduce the size of the resulting JAR.
         *
         * Those required for the plugins are available at runtime anyway.
         */
        "org/gradle/**",

        /**
         * Exclude Gradle resources in the root of the JAR.
         */
        "gradle-**",

        /**
         * Exclude everything Groovy.
         */
        "groovy**",

        /**
         * Exclude GitHub Java Parser used by Groovy.
         */
        "com/github/javaparser/**",

        /**
         * Exclude Kotlin runtime.
         */
        "kotlin/**",

        /**
         * Remove all third-party plugin declarations as well.
         *
         * They should be loaded from their respective dependencies.
         */
        "META-INF/gradle-plugins/com**",
        "META-INF/gradle-plugins/net**",
        "META-INF/gradle-plugins/org**"
    )

    isZip64 = true  /* The archive has way too many items. So using the Zip64 mode. */
    archiveClassifier.set("")  /** To prevent Gradle setting something like `osx-x86_64`. */
    mergeServiceFiles("desc.ref")
    mergeServiceFiles("META-INF/services/io.spine.option.OptionsProvider")
}

project.afterEvaluate {
    /**
     * Avoid Gradle warning on execution optimisation.
     * Presumably, this dependency should be configured by McJava.
     * Until then, have this done explicitly.
     */
    val sourcesJar: Task by tasks.getting
    val generateProto: Task by tasks.getting
    sourcesJar.dependsOn(generateProto)
}
