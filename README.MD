[![Ubuntu build][ubuntu-build-badge]][gh-actions] &nbsp;
[![codecov.io][codecov]][master-branch] &nbsp;
[![license][apache-badge]][apache-license]

[gh-actions]: https://github.com/SpineEventEngine/model-tools/actions
[ubuntu-build-badge]: https://github.com/SpineEventEngine/model-tools/actions/workflows/build-on-ubuntu.yml/badge.svg

[codecov]: https://codecov.io/github/SpineEventEngine/model-tools/coverage.svg?branch=master
[master-branch]: https://codecov.io/github/SpineEventEngine/model-toolsa?branch=master 

[apache-badge]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat
[apache-license]: http://www.apache.org/licenses/LICENSE-2.0

# Spine model verification tools

This project contains modules that implement compile time verification of the Spine model.

To enable the verification in a project, apply the following Gradle config to subprojects that may 
contain _Spine model elements_:
 ```kotlin
 buildscript {
     dependencies {
         classpath("io.spine.tools:spine-model-check-bundle:${spineVersion}")
     }
 }
 
 apply(plugin = "io.spine.tools.spine-model-check")
 
 tasks.compileJava {
     options.compilerArgs.addAll(listOf("-processor", "io.spine.model.assemble.AssignLookup", "-AspineDirRoot=${rootDir}"))
 }
 
 dependencies {
     annotationProcessor(group = "io.spine.tools", name = "spine-model-check-bundle", version = spineVersion)
 }
 ``` 
The _Spine model elements_ are Java and Protobuf declarations of Entities and the messages
handled (i.e. `Command`s, `Event`s and `Rejection`s).
