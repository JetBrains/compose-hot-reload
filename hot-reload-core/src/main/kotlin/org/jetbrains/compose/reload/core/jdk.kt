/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText

@InternalHotReloadApi
public class JavaHome(public val path: Path) {
    /**
     * The 'java' ('java.exe') executable binary inside the java distribution
     */
    public val javaExecutable: Path
        get() = path.resolve("bin").resolve(if (Os.currentOrNull() == Os.Windows) "java.exe" else "java")

    public val releaseFile: Path
        get() = path.resolve("release")

    public fun readReleaseFile(): JavaReleaseFileContent {
        return parseJavaReleaseFile(releaseFile)
    }

    override fun toString(): String {
        return "JavaHome(${path.absolutePathString()})"
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JavaHome) return false
        return path.absolutePathString() == other.path.absolutePathString()
    }

    @InternalHotReloadApi
    public companion object {
        /**
         * @param path: The 'bin/java' or 'bin/java.exe' inside a java distribution
         */
        public fun fromExecutable(path: Path): JavaHome {
            return JavaHome(path.parent.parent)
        }

        /**
         * @return The java home, currently running this code
         */
        public fun current(): JavaHome {
            System.getProperty("java.home")?.let { javaHome ->
                return JavaHome(Path(javaHome))
            }

            System.getenv("JAVA_HOME")?.let { javaHome ->
                return JavaHome(Path(javaHome))
            }

            throw IllegalStateException("Missing 'java.home' System property or 'JAVA_HOME' environment variable")
        }
    }
}

@InternalHotReloadApi
public data class JavaReleaseFileContent(
    val values: Map<String, String>
) {

    /**
     * Expected to match the 'java.vendor' System property
     * Example:
     * IMPLEMENTOR -> JetBrains s.r.o.
     */
    val implementor: String? get() = values[IMPLEMENTOR_KEY]

    /**
     *
     * Example:
     * IMPLEMENTOR_VERSION -> JBRSDK-21.0.8+9-1038.68-jcef
     */
    val implementorVersion: String? get() = values[IMPLEMENTOR_VERSION_KEY]

    /**
     * Expected to match the 'java.version' System property
     * Example:
     * JAVA_VERSION -> 21.0.8
     */
    val javaVersion: String? get() = values[JAVA_VERSION_KEY]

    /**
     * Expected to match the 'java.vm.version' System property
     * Example:
     * JAVA_RUNTIME_VERSION -> 21.0.8+9-b1038.68
     */
    val javaRuntimeVersion: String? get() = values[JAVA_RUNTIME_VERSION]

    /**
     * Example:
     * OS_ARCH -> aarch64
     */
    val osArch: String? get() = values[OS_ARCH_KEY]

    /**
     * Example:
     * OS_NAME -> Darwin
     */
    val osName: String? get() = values[OS_NAME_KEY]

    @InternalHotReloadApi
    public companion object {
        public const val IMPLEMENTOR_KEY: String = "IMPLEMENTOR"
        public const val IMPLEMENTOR_VERSION_KEY: String = "IMPLEMENTOR_VERSION"
        public const val JAVA_VERSION_KEY: String = "JAVA_VERSION"
        public const val JAVA_RUNTIME_VERSION: String = "JAVA_RUNTIME_VERSION"
        public const val OS_ARCH_KEY: String = "OS_ARCH"
        public const val OS_NAME_KEY: String = "OS_NAME"
    }

    override fun toString(): String {
        return buildString {
            for ((key, value) in values) {
                append("$key=\"$value\"\n")
            }
        }
    }
}

private fun parseJavaReleaseFile(path: Path): JavaReleaseFileContent {
    val map = buildMap {
        path.readText().lines().mapNotNull { line ->
            val keyValue = line.split("=", limit = 2)
            if (keyValue.size != 2) return@mapNotNull null
            val key = keyValue[0].trim()
            val value = keyValue[1].trim().removeSurrounding("\"")
            put(key, value)
        }
    }.toMap()

    return JavaReleaseFileContent(map)
}
