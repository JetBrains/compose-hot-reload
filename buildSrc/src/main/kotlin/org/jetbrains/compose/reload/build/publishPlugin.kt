/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.compose.reload.gradle.lazyProjectProperty

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

open class BuildPublishingExtension(project: Project) {
    val artifactId = project.objects.property<String>().value(project.name)
}

val Project.sellmairUsername by lazyProjectProperty { providers.gradleProperty("repo.sellmair.user").orNull }
val Project.sellmairPassword by lazyProjectProperty { providers.gradleProperty("repo.sellmair.password").orNull }
val Project.fireworkUsername by lazyProjectProperty { providers.gradleProperty("fireworkUsername").orNull }
val Project.fireworkPassword by lazyProjectProperty { providers.gradleProperty("fireworkPassword").orNull }

val Project.signingKeyId by lazyProjectProperty { providers.gradleProperty("signing.keyId").orNull }
val Project.signingSecretKey by lazyProjectProperty { providers.gradleProperty("signing.key").orNull }
val Project.signingPassword by lazyProjectProperty { providers.gradleProperty("signing.key.password").orNull }


class PublishingPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = withProject(target) {
        plugins.apply(MavenPublishPlugin::class.java)
        plugins.apply(SigningPlugin::class.java)

        val extension = project.extensions.create(
            "publishingConventions", BuildPublishingExtension::class.java, project
        )

        /* Configure repositories and the acutal publications */
        plugins.withType<MavenPublishPlugin>().all {
            extensions.configure<PublishingExtension> {
                repositories {
                    withProject(target) {
                        setupRepositories()
                    }
                }

                publications.withType<MavenPublication>().configureEach {
                    withProject(target) {
                        setupPublication()
                    }
                }
            }
        }

        /* Kotlin/JVM does not setup default components for publishing: Setup if necessary */
        plugins.withId("org.jetbrains.kotlin.jvm") {
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
                withJavadocJar()
            }

            extensions.configure<PublishingExtension> {
                afterEvaluate {
                    if (publications.isEmpty()) {
                        publications.create<MavenPublication>("maven") {
                            from(components["java"])
                        }
                    }
                }
            }
        }

        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            /* Maven Central requires javadocs, lets generate some stubs */
            val generateJavadocStubs = tasks.register("generateJavadocStubs") {
                val projectName = provider { project.name }
                inputs.property("projectName", projectName)

                val outputDirectory = project.layout.buildDirectory.dir("javadocStubs")
                outputs.dir(outputDirectory)
                doLast {
                    outputDirectory.get().asFile.apply {
                        mkdirs()
                        resolve(resolve("index.md")).writeText(
                            """
                            # Module ${projectName.get()}
                            Check: https://github.com/jetbrains/compose-hot-reload for further documentation
                        """.trimIndent()
                        )
                    }
                }
            }

            val javadocJar = tasks.register<Jar>("javadocJar") {
                archiveClassifier.set("javadoc")
                from(generateJavadocStubs)
            }

            extensions.configure<PublishingExtension> {
                publications.withType<MavenPublication>().configureEach {
                    artifact(javadocJar)
                }
            }
        }
    }
}

/**
 * All repositories that are used for publishing
 */
private fun RepositoryHandler.setupRepositories() = withProject {

    /*
    We publish builds for testing here:
    This repo is owned by @sellmair and can be used to quickly ship builds between machiens.
     */
    maven("https://repo.sellmair.io") {
        name = "sellmair"
        credentials {
            username = sellmairUsername
            password = sellmairPassword
        }
    }

    /*
    This repository is used for publishing dev builds
     */
    maven("https://packages.jetbrains.team/maven/p/firework/dev") {
        name = "firework"
        credentials {
            username = fireworkUsername
            password = fireworkPassword
        }
    }

    maven(rootProject.layout.buildDirectory.dir("repo")) {
        name = "local"
    }

    maven(rootProject.layout.buildDirectory.dir("bootstrap")) {
        name = "bootstrap"
    }

    maven(rootProject.layout.buildDirectory.dir("deploy")) {
        name = "deploy"
    }
}

private fun MavenPublication.setupPublication() = withProject {
    signPublicationIfKeyPresent()

    suppressPomMetadataWarningsFor("testFixturesApiElements")
    suppressPomMetadataWarningsFor("testFixturesRuntimeElements")

    afterEvaluate {
        artifactId = artifactId.replace(
            project.name, project.extensions.getByType<BuildPublishingExtension>().artifactId.get()
        )
    }

    pom {
        name.set(project.name)
        description.set("Compose Hot Reload implementation")
        url.set("https://github.com/JetBrains/compose-hot-reload")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("JetBrains")
                name.set("JetBrains Team")
                organization.set("JetBrains")
                organizationUrl.set("https://www.jetbrains.com")
            }
        }

        scm {
            url.set("https://github.com/JetBrains/compose-hot-reload")
        }
    }
}

private fun MavenPublication.signPublicationIfKeyPresent() = withProject {
    project.extensions.configure<SigningExtension>("signing") {
        if (signingSecretKey.isNullOrBlank() || signingPassword.isNullOrBlank()) {
            isRequired = false
            return@configure
        }

        useInMemoryPgpKeys(signingKeyId, signingSecretKey, signingPassword)
        sign(this@signPublicationIfKeyPresent)

        tasks.withType<PublishToMavenRepository>().configureEach {
            dependsOn(tasks.withType<Sign>())
        }
    }
}
