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

package io.spine.model.check;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import io.spine.logging.Logging;
import io.spine.model.CommandReceivers;
import io.spine.server.command.model.DuplicateHandlerCheck;
import io.spine.server.model.Model;
import io.spine.tools.gradle.project.ProjectHierarchy;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.function.Function;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.flogger.LazyArgs.lazy;
import static java.lang.String.format;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * A utility for verifying Spine model.
 *
 * @implNote The full name of this class is used by {@link Model#dropAllModels()} via a
 *           string literal for security check.
 */
public class ModelCheck implements Logging {

    private static final URL[] EMPTY_URL_ARRAY = new URL[0];

    private final URLClassLoader projectClassLoader;

    /**
     * Creates a new instance.
     *
     * @param project
     *         the Gradle project to verify the model upon
     */
    public ModelCheck(Project project) {
        this.projectClassLoader = createClassLoader(project);
    }

    /**
     * Verifies Spine model upon the given Gradle project.
     *
     * @param receivers
     *         the listing of the Spine model classes
     */
    public void verify(CommandReceivers receivers) {
        var classSet = new ClassSet(projectClassLoader,
                                    receivers.getCommandReceivingTypeList());
        reportNotFoundIfAny(classSet);
        DuplicateHandlerCheck.newInstance()
                             .check(classSet.elements());
    }

    @SuppressWarnings("FloggerLogString") // Cannot make it compile-time constant here.
    private void reportNotFoundIfAny(ClassSet classSet) {
        var notFound = classSet.notFound();
        if (notFound.isEmpty()) {
            return;
        }
        var msg = "Failed to load "
                + (notFound.size() > 1 ? "classes " : "the class ")
                + Joiner.on(", ")
                        .join(notFound)
                + format(".%n")
                + "Consider using the `io.spine.tools.spine-model-verifier` plugin" +
                " only for the modules with the sufficient classpath.";
        _warn().log(msg);
    }

    /**
     * Creates a ClassLoader for the passed project.
     */
    private URLClassLoader createClassLoader(Project project) {
        var tasks = allJavaCompile(project);
        var compiledCodePath = extractDestinationDirs(tasks);
        _debug().log("Initializing `ClassLoader` for URLs: `%s`.",
                     lazy(() -> deepToString(compiledCodePath)));
        try {
            var projectClassloader = project.getBuildscript().getClassLoader();
            @SuppressWarnings("ClassLoaderInstantiation") // Caught exception.
            var result = new URLClassLoader(compiledCodePath, projectClassloader);
            return result;
        } catch (SecurityException e) {
            var msg = format("Cannot create `ClassLoader` for the project `%s`.", project);
            throw new IllegalStateException(msg, e);
        }
    }

    private static Collection<JavaCompile> allJavaCompile(Project project) {
        Collection<JavaCompile> tasks = newArrayList();
        ProjectHierarchy.applyToAll(project.getRootProject(),
                                    p -> tasks.addAll(javaCompile(p)));
        return tasks;
    }

    private static Collection<JavaCompile> javaCompile(Project project) {
        return project.getTasks()
                      .withType(JavaCompile.class);
    }

    private static URL[] extractDestinationDirs(Collection<JavaCompile> tasks) {
        Collection<URL> urls = tasks.stream()
                                    .map(GetDestinationDir.FUNCTION)
                                    .collect(toList());
        var result = urls.toArray(EMPTY_URL_ARRAY);
        return result;
    }

    /**
     * A function which retrieves the output directory from the passed Gradle task.
     */
    @VisibleForTesting
    enum GetDestinationDir implements Function<JavaCompile, URL> {
        FUNCTION;

        @Override
        public @Nullable URL apply(@Nullable JavaCompile task) {
            requireNonNull(task);
            var directory = task.getDestinationDirectory();
            if (!directory.isPresent()) {
                return null;
            }
            var dir = directory.getAsFile().get();
            var uri = dir.toURI();
            try {
                var url = uri.toURL();
                return url;
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(format(
                        "Could not retrieve destination directory for task `%s`.",
                        task.getName()), e);
            }
        }
    }
}
