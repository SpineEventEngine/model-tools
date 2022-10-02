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

package io.spine.model.verify;

import io.spine.testing.SlowTest;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.model.ModelTests;
import io.spine.tools.gradle.testing.GradleProject;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static com.google.common.collect.ImmutableList.toImmutableList;
//import static io.spine.tools.mc.java.gradle.plugins.ModelVerifierTaskName.verifyModel;
import static io.spine.model.verify.ModelVerifierTaskName.verifyModel;
import static org.gradle.testkit.runner.TaskOutcome.FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SlowTest
@DisplayName("`ModelVerifierPlugin` should")
class ModelVerifierPluginTest {

    private static final String PROJECT_NAME = "model-verifier-test";
    private static final String VALID_AGGREGATE_JAVA =
            "io/spine/model/verify/ValidAggregate.java";

    @TempDir
    @SuppressWarnings("PackageVisibleField") // must be non-private for JUnit's annotation to work.
    File tempDir;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
    }

    @Test
    @DisplayName("pass valid model classes")
    void passValidModelClasses() {
        newProjectWithJava(VALID_AGGREGATE_JAVA,
                           "io/spine/model/verify/ValidProcMan.java",
                           "io/spine/model/verify/ValidCommandHandler.java")
                .executeTask(verifyModel);
    }

    @Test
    @MuteLogging
    @DisplayName("halt build on duplicate command-handling methods")
    void rejectDuplicateHandlingMethods() {
        var project = newProjectWithJava(
                "io/spine/model/verify/DuplicateAggregate.java",
                "io/spine/model/verify/DuplicateCommandAssignee.java"
        );
        var result = project.executeAndFail(verifyModel);
        var task = result.task(verifyModel.path());
        assertNotNull(task, result.getOutput());
        var generationResult = task.getOutcome();
        assertEquals(FAILED, generationResult, result.getOutput());
    }

    @Test
    @DisplayName("ignore duplicate entries in a Gradle project")
    void ignoreDuplicateEntries() {
        var project = newProjectWithJava(VALID_AGGREGATE_JAVA);
        project.executeTask(verifyModel);
        project.executeTask(verifyModel);
    }

    @Test
    @DisplayName("halt build on malformed command-handling methods")
    void rejectMalformedHandlingMethods() {
        var result = newProjectWithJava("io/spine/model/verify/MalformedAggregate.java")
                .executeAndFail(verifyModel);
        var task = result.task(verifyModel.path());
        assertNotNull(task, result.getOutput());
        var generationResult = task.getOutcome();
        assertEquals(FAILED, generationResult, result.getOutput());
    }

    private GradleProject newProjectWithJava(String... fileNames) {
        var filesWithBuild = new ArrayList<>(Arrays.asList(fileNames));
        filesWithBuild.add("build.gradle.kts");
        var javaFiles = filesWithBuild.stream()
                .map(Paths::get)
                .collect(toImmutableList());
        Function1<Path, Boolean> matching = path -> {
            var isWantedJavaFile = javaFiles.stream().anyMatch(path::endsWith);
            return isWantedJavaFile || path.toString().endsWith(".proto");
        };
        return GradleProject.setupAt(tempDir)
                .fromResources(PROJECT_NAME, matching)
                .copyBuildSrc()
                .create();
    }
}
