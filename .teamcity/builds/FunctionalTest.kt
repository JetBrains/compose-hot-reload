/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package builds

import builds.conventions.BuildCacheConvention
import builds.conventions.CommitStatusPublisher
import builds.conventions.HardwareCapacity
import builds.conventions.HostRequirement
import builds.conventions.PublishLocallyConvention
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun functionalTests(): List<FunctionalTest> {
    return listOf(
        FunctionalTest(bucket = 1, bucketsCount = 7),
        FunctionalTest(bucket = 2, bucketsCount = 7),
        FunctionalTest(bucket = 3, bucketsCount = 7),
        FunctionalTest(bucket = 4, bucketsCount = 7),
        FunctionalTest(bucket = 5, bucketsCount = 7),
        FunctionalTest(bucket = 6, bucketsCount = 7),
        FunctionalTest(bucket = 7, bucketsCount = 7),
    )
}

@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
class FunctionalTest(
    private val bucket: Int? = null,
    private val bucketsCount: Int? = null,
) : BuildType({
    val key = run {
        val hash = (bucket.toString() + bucketsCount).hashCode()

        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        buffer.putInt(hash)
        Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array()).replace("-", "_")
    }

    name = buildString {
        append("Functional Test: (")
        append(
            buildList {
                if (bucket != null && bucketsCount != null) {
                    add("$bucket/$bucketsCount")
                }
            }.joinToString(", ")
        )

        append(")")
    }

    id("FunctionalTest_$key")

    artifactRules = """
        **/*-actual*
        **/build/reports/** => reports.zip
        **/build/logs/** => logs.zip
    """.trimIndent()

    params {
        if (bucket != null && bucketsCount != null) {
            param("env.TESTED_BUCKET", bucket.toString())
            param("env.TESTED_BUCKETS_COUNT", bucketsCount.toString())
        }
    }

    steps {
        gradle {
            name = "Test"
            tasks = "reloadFunctionalTest --continue -x publishLocally -Pchr.tests.parallelism=2"
        }
    }
}), PublishLocallyConvention,
    CommitStatusPublisher,
    HostRequirement.Linux,
    HardwareCapacity.Large,
    BuildCacheConvention.Consumer
