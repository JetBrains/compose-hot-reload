/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds

import builds.conventions.CommitStatusPublisher
import builds.conventions.HardwareCapacity
import builds.conventions.HostRequirement
import builds.conventions.PublishLocallyConvention
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.buildCache
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.Path
import kotlin.io.path.readText

fun functionalTests(): List<FunctionalTest> {
    val json = Path("testDimensions.json")
    val root = Json.parseToJsonElement(json.readText()) as JsonObject
    val kotlinVersions = root.getValue("kotlin") as JsonArray
    val composeVersions = root.getValue("compose") as JsonArray

    return kotlinVersions.flatMap { kotlinVersion ->
        composeVersions.flatMap { composeVersion ->
            val kotlinVersionString = kotlinVersion.jsonObject.getValue("version").jsonPrimitive.content
            val composeVersionString = composeVersion.jsonObject.getValue("version").jsonPrimitive.content
            val isDefaultKotlinVersion = kotlinVersion.jsonObject["isDefault"]?.jsonPrimitive?.booleanOrNull == true
            val isDefaultComposeVersion = composeVersion.jsonObject["isDefault"]?.jsonPrimitive?.booleanOrNull == true

            // Create buckets
            if (isDefaultKotlinVersion && isDefaultComposeVersion) {
                return@flatMap listOf(
                    FunctionalTest(kotlinVersionString, composeVersionString, bucket = 1, bucketsCount = 2),
                    FunctionalTest(kotlinVersionString, composeVersionString, bucket = 2, bucketsCount = 2)
                )
            }

            listOf(FunctionalTest(kotlinVersionString, composeVersionString))
        }
    }.plus(FunctionalTest(kotlinVersion = null, composeVersion = null, defaultCompilerOptions = false))
}

@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
class FunctionalTest(
    private val kotlinVersion: String?,
    private val composeVersion: String?,
    private val defaultCompilerOptions: Boolean = true,
    private val bucket: Int? = null,
    private val bucketsCount: Int? = null,
) : BuildType({
    val key = run {
        val hash = (kotlinVersion + composeVersion + defaultCompilerOptions + bucket + bucketsCount).hashCode()
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        buffer.putInt(hash)
        Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array()).replace("-", "_")
    }

    name = buildString {
        append("Functional Test: (")
        append(
            buildList {
                if (kotlinVersion != null) {
                    add("Kotlin $kotlinVersion")
                }

                if (composeVersion != null) {
                    add("Compose $composeVersion")
                }

                if (!defaultCompilerOptions) {
                    add("Non default compiler options")
                }

                if (bucket != null && bucketsCount != null) {
                    add("($bucket/$bucketsCount)")
                }
            }.joinToString(", ")
        )

        append(")")
    }

    id("FunctionalTest_$key")

    artifactRules = """
        **/*-actual*
        **/build/reports/** => reports.zip
    """.trimIndent()

    params {
        if (kotlinVersion != null) {
            param("env.TESTED_KOTLIN_VERSION", kotlinVersion)
        }

        if (composeVersion != null) {
            param("env.TESTED_COMPOSE_VERSION", composeVersion)
        }

        if (bucket != null && bucketsCount != null) {
            param("env.TESTED_BUCKET", bucket.toString())
            param("env.TESTED_BUCKETS_COUNT", bucketsCount.toString())
        }

        param("env.TESTED_DEFAULT_COMPILER_OPTIONS", defaultCompilerOptions.toString())
    }

    features {
        buildCache {
            use = true
            publish = true
            name = "(${key}) Functional Test Gradle Cache (modules-2)"
            rules = """
                tests/build/gradleHome/caches/modules-2/files-2.1
                tests/build/gradleHome/caches/modules-2/metadata-2.106
                tests/build/gradleHome/caches/modules-2/metadata-2.107
            """.trimIndent()
        }

        buildCache {
            use = true
            publish = true
            name = "(${key}) Functional Test Gradle Cache (build-cache-2)"
            rules = """
                tests/build/gradleHome/caches/build-cache-1
            """.trimIndent()
        }


        buildCache {
            use = true
            publish = true
            name = "(${key}) Functional Test Gradle Cache (wrapper)"
            rules = """
                tests/build/gradleHome/wrapper
            """.trimIndent()
        }
    }

    steps {
        gradle {
            name = "Test"
            tasks = "reloadFunctionalTest --continue -x publishLocally -Pchr.tests.sequential"
        }
    }
}),
    PublishLocallyConvention,
    CommitStatusPublisher,
    HostRequirement.Linux,
    HardwareCapacity.Medium
