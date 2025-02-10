import gradle.kotlin.dsl.accessors._285dcef16d8875fee0ec91e18e07daf9.kotlin

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    `maven-publish` apply false
    signing
}

private val sellmairUsername = providers.gradleProperty("repo.sellmair.user").orNull
private val sellmairPassword = providers.gradleProperty("repo.sellmair.password").orNull
private val fireworkUsername = providers.gradleProperty("fireworkUsername").orNull
private val fireworkPassword = providers.gradleProperty("fireworkPassword").orNull

private val signingKeyId = providers.gradleProperty("signing.keyId").orNull
private val signingSecretKey = providers.gradleProperty("signing.key").orNull
private val signingPassword = providers.gradleProperty("signing.key.password").orNull

plugins.withType<MavenPublishPlugin>().all {
    publishing {
        repositories {
            maven("https://repo.sellmair.io") {
                name = "sellmair"
                credentials {
                    username = sellmairUsername
                    password = sellmairPassword
                }
            }

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

            maven(rootProject.layout.buildDirectory.dir("deploy")) {
                name = "deploy"
            }
        }

        publications.withType<MavenPublication>().configureEach {
            signPublicationIfKeyPresent()

            artifactId = project.name.removePrefix("hot-reload-")

            pom {
                name = project.name
                description = "Compose Hot Reload implementation"
                url = "https://github.com/JetBrains/compose-hot-reload"

                licenses {
                    license {
                        name = "The Apache Software License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }

                developers {
                    developer {
                        id = "JetBrains"
                        name = "JetBrains Team"
                        organization = "JetBrains"
                        organizationUrl = "https://www.jetbrains.com"
                    }
                }

                scm {
                    url = "https://github.com/JetBrains/compose-hot-reload"
                }
            }
        }
    }
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    publishing {
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

    publishing {
        publications.withType<MavenPublication>().configureEach {
            artifact(javadocJar)
        }
    }
}

fun MavenPublication.signPublicationIfKeyPresent() {
    if (signingSecretKey.isNullOrBlank()) return
    if (signingPassword.isNullOrBlank()) return

    extensions.configure<SigningExtension>("signing") {
        useInMemoryPgpKeys(signingKeyId, signingSecretKey, signingPassword)
        sign(this@signPublicationIfKeyPresent)

        tasks.withType<PublishToMavenRepository>().configureEach {
            dependsOn(tasks.withType<Sign>())
        }
    }
}
