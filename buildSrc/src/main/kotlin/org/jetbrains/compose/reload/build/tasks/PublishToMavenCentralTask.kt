/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build.tasks

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.util.Base64
import kotlin.io.path.createDirectories

abstract class PublishToMavenCentralTask : DefaultTask() {

    @get:Input
    val sonatypeUser = project.objects.property<String>()
        .value(project.providers.gradleProperty("sonatype.user"))


    @get:Input
    val sonatypeToken = project.objects.property<String>()
        .value(project.providers.gradleProperty("sonatype.token"))


    @Input
    val deploymentName = project.objects.property<String>()
        .convention(project.provider { "compose-hot-reload-${project.version}" })

    @InputFile
    val deploymentBundle = project.objects.fileProperty()

    @OutputFile
    val deploymentIdFile = project.objects.fileProperty()
        .convention(project.layout.buildDirectory.file("mavenCentral.deploy.id.txt"))

    @TaskAction
    fun publish() {
        runBlocking {
            HttpClient(CIO).use { client ->
                client.publish()
            }
        }
    }

    private suspend fun HttpClient.publish() {
        val bearerToken = Base64.getEncoder().encode(
            "${sonatypeUser.get()}:${sonatypeToken.get()}".toByteArray()
        ).toString(Charsets.UTF_8)

        val response = submitForm {
            url("https://central.sonatype.com/api/v1/publisher/upload")
            parameter("name", deploymentName.get())
            parameter("publishingType", "AUTOMATIC")
            bearerAuth(bearerToken)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("bundle", deploymentBundle.get().asFile.readBytes(), headers {
                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"bundle.zip\"")
                        })
                    }
                )
            )
            onUpload { bytesSentTotal, contentLength ->
                logger.info("Sent $bytesSentTotal bytes from $contentLength")
            }
        }

        if (response.status != HttpStatusCode.Companion.Created) {
            error("Deployment failed (${response.status}):\n ${response.bodyAsText()}")
        }

        val deploymentId = response.bodyAsText().trim()
        deploymentIdFile.get().asFile.parentFile.toPath().createDirectories()
        deploymentIdFile.get().asFile.writeText(deploymentId)
        logger.quiet("Deployment ID: $deploymentId")

        while (true) {
            logger.quiet("Checking Deployment Status: $deploymentId")

            val statusResponse = post {
                bearerAuth(bearerToken)
                accept(ContentType.Application.Json)
                url("https://central.sonatype.com/api/v1/publisher/status")
                parameter("id", deploymentId)
            }
            if (statusResponse.status != HttpStatusCode.Companion.OK) {
                error("Deployment failed (${statusResponse.status}):\n ${statusResponse.bodyAsText()}")
            }

            val body = statusResponse.bodyAsText()
            logger.quiet(body)

            when (deploymentState(body)) {
                "PUBLISHED" -> break
                "FAILED" -> {
                    /*
                     * A deployment that fails *only* because every component already exists means this exact
                     * version was published before (e.g. a previous run uploaded successfully but reported a
                     * transient gateway error such as '502 Bad Gateway'). Re-publishing the same version is a
                     * no-op on Maven Central, so we treat it as success to keep this task idempotent instead of
                     * blocking the release forever.
                     */
                    if (isAlreadyPublished(body)) {
                        logger.warn(
                            "Deployment '${deploymentName.get()}' is already published to Maven Central " +
                                "(all components already exist); treating as success."
                        )
                        break
                    }
                    error("Deployment failed (${statusResponse.status}):\n $body")
                }
            }
        }
    }

    private fun deploymentState(body: String): String? =
        Json.parseToJsonElement(body).jsonObject["deploymentState"]?.jsonPrimitive?.content

    /**
     * `true` when the deployment failed and every reported error is an "already exists" error, i.e. the
     * deployment failed solely because this version is already present on Maven Central.
     */
    private fun isAlreadyPublished(body: String): Boolean {
        val errors = Json.parseToJsonElement(body).jsonObject["errors"]?.jsonObject ?: return false
        val messages = errors.values.flatMap { value -> value.jsonArray.map { it.jsonPrimitive.content } }
        return messages.isNotEmpty() && messages.all { "already exists" in it }
    }
}
