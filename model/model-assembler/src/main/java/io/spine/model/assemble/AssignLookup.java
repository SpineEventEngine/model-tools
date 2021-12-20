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

package io.spine.model.assemble;

import com.google.common.annotations.VisibleForTesting;
import io.spine.annotation.Internal;
import io.spine.model.CommandReceivers;
import io.spine.server.command.Assign;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.newTreeSet;
import static io.spine.io.Ensure.ensureFile;
import static io.spine.io.Files2.existsNonEmpty;
import static io.spine.protobuf.Messages.isDefault;

/**
 * An annotation processor for {@link Assign @Assign} annotation.
 *
 * <p>Collects the types which contain command assignee methods (marked with {@code @Assign}
 * annotation) and writes them into the {@code ${spineDirRoot}/.spine/spine_model.ser} file, where
 * "{@code spineDirRoot}" is the value of the <b>spineDirRoot</b> annotator option.
 *
 * <p><b>spineDirRoot</b> is the only supported option of the processor.
 * Use {@code javac -AspineDirRoot=/path/to/project/root [...]} to set the value of the option.
 * If none is set, the option will default to current directory (denoted with "{@code ./}").
 */
public class AssignLookup extends SpineAnnotationProcessor {

    @Internal
    public static final String DESTINATION_PATH = ".spine/spine_model.ser";
    @VisibleForTesting
    static final String OUTPUT_OPTION_NAME = "spineDirRoot";
    private static final String DEFAULT_OUTPUT_OPTION = ".";

    /**
     * List of {@link io.spine.server.command.CommandAssignee command assignee}s.
     *
     * @implNote The {@linkplain CommandReceivers type} of this filed implies that it can store
     *           {@code CommandReceiver}s, which could be either {@code CommandAssignee}
     *           or {@code Commander}. But we are going to store only assignees there.
     */
    private final CommandReceivers.Builder commandAssignees = CommandReceivers.newBuilder();

    @Override
    protected Class<? extends Annotation> getAnnotationType() {
        return Assign.class;
    }

    @Override
    public Set<String> getSupportedOptions() {
        Set<String> result = newHashSet(super.getSupportedOptions());
        result.add(OUTPUT_OPTION_NAME);
        return result;
    }

    @SuppressWarnings("CheckReturnValue") // calling builder
    @Override
    protected void processElement(Element element) {
        var enclosingTypeElement = (TypeElement) element.getEnclosingElement();
        var typeName = enclosingTypeElement.getQualifiedName()
                                           .toString();
        commandAssignees.addCommandReceiverType(typeName);
    }

    @Override
    protected void onRoundFinished() {
        var spineOutput = getOption(OUTPUT_OPTION_NAME).orElse(DEFAULT_OUTPUT_OPTION);
        var fileName = spineOutput + '/' + DESTINATION_PATH;
        var serializedModelStorage = new File(fileName);
        mergeOldAssigneesFrom(serializedModelStorage);
        writeAssigneesTo(serializedModelStorage);
    }

    /**
     * Merges the currently built {@link AssignLookup#commandAssignees} with the pre-built one.
     *
     * <p>If the file exists and is not empty, the message of type {@link CommandReceivers} is
     * read from it and merged with the current commandAssignees by the rules of
     * {@link com.google.protobuf.Message.Builder#mergeFrom(com.google.protobuf.Message)
     * Message.Builder.mergeFrom()}.
     *
     * @param file the file which may or may not contain the pre-assembled commandAssignees
     */
    @SuppressWarnings("CheckReturnValue") // calling builder
    private void mergeOldAssigneesFrom(File file) {
        var fileWithData = existsNonEmpty(file);
        if (fileWithData) {
            var preexistingModel = readExisting(file);
            commandAssignees.mergeFrom(preexistingModel);
        }
    }

    /**
     * Writes the {@link AssignLookup#commandAssignees} to the given file.
     *
     * <p>If the given file does not exist, this method creates it.
     *
     * <p>The written commandAssignees will be cleaned from duplications in the repeated fields.
     *
     * <p>The I/O errors are handled by rethrowing them as {@link IllegalStateException}.
     *
     * @param file an existing file to write the commandAssignees into
     */
    private void writeAssigneesTo(File file) {
        ensureFile(file);
        removeDuplicates();
        var serializedModel = commandAssignees.vBuild();
        if (!isDefault(serializedModel)) {
            try (var out = new FileOutputStream(file)) {
                serializedModel.writeTo(out);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Cleans the currently built {@link #commandAssignees} from the duplicates.
     *
     * <p>Calling this method will cause the current list of assignees not to contain
     * duplicate entries in any {@code repeated} field.
     */
    @SuppressWarnings("CheckReturnValue") // calling builder
    private void removeDuplicates() {
        var list = commandAssignees.getCommandReceiverTypeList();
        Set<String> types = newTreeSet(list);
        commandAssignees.clearCommandReceiverType()
                        .addAllCommandReceiverType(types);
    }

    /**
     * Reads the existing {@link #commandAssignees} from the given file.
     *
     * <p>The given file should exist.
     *
     * <p>If the given file is empty,
     * the {@link CommandReceivers#getDefaultInstance() CommandAssignees.getDefaultInstance()} is
     * returned.
     *
     * @param file an existing file with a {@link CommandReceivers} message
     * @return the read commandAssignees
     */
    private static CommandReceivers readExisting(File file) {
        if (file.length() == 0) {
            return CommandReceivers.getDefaultInstance();
        } else {
            try (InputStream in = new FileInputStream(file)) {
                var preexistingModel = CommandReceivers.parseFrom(in);
                return preexistingModel;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
