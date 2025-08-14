/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi

@InternalHotReloadApi
public class Version(
    public val version: String
) : Comparable<Version> {

    public val major: Int
    public val minor: Int
    public val patch: Int
    public val qualifier: String?

    init {
        val match = versionRegex.find(version) ?: throw IllegalArgumentException("Invalid version: $version")
        major = match.groups["major"]?.value?.toInt() ?: 0
        minor = match.groups["minor"]?.value?.toInt() ?: 0
        patch = match.groups["patch"]?.value?.toInt() ?: 0
        qualifier = match.groups["qualifier"]?.value
    }

    override fun compareTo(other: Version): Int {
        return compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { Qualifier(it.qualifier) })
    }

    override fun toString(): String {
        return version
    }

    override fun hashCode(): Int {
        return version.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Version) return false
        return version == other.version
    }
}

private class Qualifier(value: String?) : Comparable<Qualifier> {
    val splits = value?.split(".") ?: emptyList()

    override fun compareTo(other: Qualifier): Int {
        splits.withIndex().forEach { (index, thisSplit) ->
            val otherSplit = other.splits.getOrNull(index) ?: return -1
            val thisSplitNumber = thisSplit.toIntOrNull()
            val otherSplitNumber = otherSplit.toIntOrNull()

            if (thisSplitNumber != null && otherSplitNumber != null) {
                if (thisSplitNumber != otherSplitNumber) return thisSplitNumber.compareTo(otherSplitNumber)
            } else if (thisSplit != otherSplit) {
                return thisSplit.compareTo(otherSplit)
            }
        }

        return 0
    }
}

private val versionRegex by lazy {
    Regex("""^(?<major>\d+)(\.(?<minor>\d+)(\.(?<patch>\d+))?)?(-(?<qualifier>([\w\d.]+)))?""")
}
