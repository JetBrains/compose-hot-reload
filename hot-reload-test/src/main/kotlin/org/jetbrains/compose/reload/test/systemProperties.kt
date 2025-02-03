package org.jetbrains.compose.reload.test

import java.io.File
import kotlin.io.path.Path

internal val compileClasspath = requireEnvVar("chr.compilePath").split(File.pathSeparator).map(::Path)

internal val compilePluginClasspath = requireEnvVar("chr.compilePluginPath").split(File.pathSeparator).map(::Path)

internal val compileModuleName = requireEnvVar("chr.compileModuleName").toString()

internal fun applicationClassesDir() = Path(requireSystemProperty("applicationClassesDir"))

internal fun requireSystemProperty(key: String): String {
    return System.getProperty(key) ?: error("System Property '$key' is not defined")
}

internal fun requireEnvVar(key: String): String {
    return System.getenv(key) ?: error("Environment Variable '$key' is not defined")
}
