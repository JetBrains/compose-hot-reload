/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("FunctionName", "DuplicatedCode")

package org.jetbrains.compose.reload.test.gradle

import java.io.Serializable

public fun ComposeVersion(composeVersionString: String): ComposeVersion {
    val delimPos =
        composeVersionString.indexOf("-").takeIf { it > 0 } ?: composeVersionString.indexOf("+").takeIf { it > 0 }
    val baseVersion = composeVersionString.substring(0, delimPos ?: composeVersionString.length)
    val classifier = delimPos?.let { composeVersionString.substring(it) }

    val baseVersionSplit = baseVersion.split(".")

    val majorVersion = baseVersionSplit[0].toIntOrNull()
    val minorVersion = baseVersionSplit.getOrNull(1)?.toIntOrNull()
    val patchVersion = baseVersionSplit.getOrNull(2)?.toIntOrNull()

    if (majorVersion == null || minorVersion == null || patchVersion == null) {
        throw IllegalArgumentException("Invalid Compose version: $composeVersionString (Failed parsing major/minor/patch version)")
    }

    return ComposeVersion(
        major = majorVersion,
        minor = minorVersion,
        patch = patchVersion,
        classifier = classifier
    )
}

public class ComposeVersion(
    public val major: Int,
    public val minor: Int,
    public val patch: Int,
    public val classifier: String?
) : Comparable<ComposeVersion>, Serializable {

    public enum class Maturity {
        SNAPSHOT, DEV, ALPHA, BETA, RC, STABLE
    }

    public val maturity: Maturity = run {
        val classifier = this.classifier?.lowercase()
        when {
            classifier == null -> Maturity.STABLE
            classifier.matches(Regex("""(-rc)(\d*)?""")) -> Maturity.RC
            classifier.matches(Regex("""-beta(\d*)?""")) -> Maturity.BETA
            classifier.matches(Regex("""-alpha(\d*)?""")) -> Maturity.ALPHA
            classifier.matches(Regex("""\+dev(\d*)?""")) -> Maturity.DEV
            else -> Maturity.SNAPSHOT // custom specifier that could be used for local builds
        }
    }

    override fun compareTo(other: ComposeVersion): Int {
        if (this == other) return 0
        (this.major - other.major).takeIf { it != 0 }?.let { return it }
        (this.minor - other.minor).takeIf { it != 0 }?.let { return it }
        (this.patch - other.patch).takeIf { it != 0 }?.let { return it }
        (this.maturity.ordinal - other.maturity.ordinal).takeIf { it != 0 }?.let { return it }

        if (this.classifier == null && other.classifier != null) {
            /* eg. 1.6.20 > 1.6.20-alpha */
            return 1
        }

        if (this.classifier != null && other.classifier == null) {
            /* e.g. 1.6.20-alpha < 1.6.20 */
            return -1
        }

        val thisClassifierNumber = this.classifierNumber
        val otherClassifierNumber = other.classifierNumber
        if (thisClassifierNumber != null && otherClassifierNumber != null) {
            (thisClassifierNumber - otherClassifierNumber).takeIf { it != 0 }?.let { return it }
        }

        if (thisClassifierNumber != null && otherClassifierNumber == null) {
            /* e.g. 1.6.20-rc1 > 1.6.20-rc */
            return 1
        }

        if (thisClassifierNumber == null && otherClassifierNumber != null) {
            /* e.g. 1.6.20-rc < 1.6.20-rc1 */
            return -1
        }

        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposeVersion) return false
        if (this.major != other.major) return false
        if (this.minor != other.minor) return false
        if (this.patch != other.patch) return false
        if (this.classifier?.lowercase() != other.classifier?.lowercase()) return false
        return true
    }

    override fun hashCode(): Int {
        var result = major
        result = 31 * result + minor
        result = 31 * result + patch
        result = 31 * result + (classifier?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "$major.$minor.$patch" + if (classifier != null) "$classifier" else ""
    }
}

public val ComposeVersion.classifierNumber: Int?
    get() {
        if (classifier == null) return null

        val classifierRegex = Regex("""(.+?)(\d*)?""")
        val classifierMatch = classifierRegex.matchEntire(classifier) ?: return null
        return classifierMatch.groupValues.getOrNull(2)?.toIntOrNull()
    }

public operator fun String.compareTo(version: ComposeVersion): Int {
    return ComposeVersion(this).compareTo(version)
}

public operator fun ComposeVersion.compareTo(version: String): Int {
    return this.compareTo(ComposeVersion(version))
}


