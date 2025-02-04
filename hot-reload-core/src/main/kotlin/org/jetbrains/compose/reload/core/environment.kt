package org.jetbrains.compose.reload.core

import java.io.File
import java.lang.System.getProperty
import java.nio.file.Path
import kotlin.io.path.Path

public enum class HotReloadProperty(public val key: String) {
    OrchestrationPort("compose.reload.orchestration.port"),
    PidFile("compose.reload.pidFile"),
    IsHeadless("compose.reload.headless"),
    HotClasspath("compose.reload.hotApplicationClasspath"),

    BuildSystem("compose.reload.buildSystem"),

    GradleJavaHome("org.gradle.java.home"),
    GradleBuildRoot("gradle.build.root"),
    GradleBuildProject("gradle.build.project"),
    GradleBuildTask("gradle.build.task"),
    GradleBuildContinuous("gradle.build.continuous"),

    AmperBuildRoot("amper.build.root"),
    AmperBuildTask("amper.build.task"),

    DevToolsEnabled("compose.reload.devToolsEnabled"),
    DevToolsClasspath("compose.reload.devToolsClasspath"),

    /**
     * Note: Expected as an environment variable, as this is expected to be transitively available
     * to all child processes.
     *
     * Currently, launching applications with hot reload might be done through a couple of
     * intermediate processes. For example, launching a test will go through a chain like
     *
     * ```
     * intellij --launches--> Gradle --launches--> JVM(Junit) --launches--> Gradle
     * --launches--> JVM (Application)
     * ```
     *
     * When a run configuration is started in 'debug mode' intellij will set the system property
     * 'idea.debugger.dispatch.port'. This will indicate that a server is listening at this port, which can
     * be used to provision debugging servers.
     *
     * This debug port will then be made available as an environment variable using this key.
     * Launching the final application will respect this port, if present and provision a debugging session.
     *
     * This will allow a test to be deeply debuggable by just pressing 'Debug'
     */
    IntelliJDebuggerDispatchPort("compose.reload.idea.debugger.dispatch.port"),
    ;

    override fun toString(): String {
        return key
    }

}

public object HotReloadEnvironment {
    public val orchestrationPort: Int? = systemInt(HotReloadProperty.OrchestrationPort)
    public val pidFile: Path? = system(HotReloadProperty.PidFile)?.let(::Path)
    public val isHeadless: Boolean = systemBoolean(HotReloadProperty.IsHeadless, false)
    public val hotApplicationClasspath: List<Path>? = systemFiles(HotReloadProperty.HotClasspath)

    public val buildSystem: BuildSystem = systemEnum<BuildSystem>(
        property = HotReloadProperty.BuildSystem,
        defaultValue = BuildSystem.Gradle,
    )

    public val gradleJavaHome: Path? = system(HotReloadProperty.GradleJavaHome)?.let(::Path)
    public val gradleBuildRoot: String? = system(HotReloadProperty.GradleBuildRoot)
    public val gradleBuildProject: String? = system(HotReloadProperty.GradleBuildProject)
    public val gradleBuildTask: String? = system(HotReloadProperty.GradleBuildTask)
    public val gradleBuildContinuous: Boolean = systemBoolean(HotReloadProperty.GradleBuildContinuous, false)

    public val amperBuildRoot: String? = system(HotReloadProperty.AmperBuildRoot)
    public val amperBuildTask: String? = system(HotReloadProperty.AmperBuildTask)

    public val devToolsEnabled: Boolean = systemBoolean(HotReloadProperty.DevToolsEnabled, true)
    public val devToolsClasspath: List<Path>? = systemFiles(HotReloadProperty.DevToolsClasspath)

    /**
     * @see HotReloadProperty.IntelliJDebuggerDispatchPort
     */
    public val intellijDebuggerDispatchPort: Int? = environmentInt(HotReloadProperty.IntelliJDebuggerDispatchPort)
}

public fun system(property: HotReloadProperty): String? = getProperty(property.key)

public fun systemBoolean(property: HotReloadProperty, default: Boolean): Boolean =
    getProperty(property.key)?.toBooleanStrict() ?: default

public fun systemInt(property: HotReloadProperty): Int? =
    getProperty(property.key)?.toIntOrNull()

public fun systemFiles(property: HotReloadProperty): List<Path>? =
    getProperty(property.key)?.split(File.pathSeparator)?.map(::Path)

public inline fun <reified T : Enum<T>> systemEnum(property: HotReloadProperty): T? =
    getProperty(property.key)?.let { enum -> return enumValueOf<T>(enum) }

public inline fun <reified T : Enum<T>> systemEnum(property: HotReloadProperty, defaultValue: T): T =
    systemEnum<T>(property) ?: defaultValue

public fun environment(property: HotReloadProperty): String? = System.getenv(property.key)

public fun environment(property: HotReloadProperty, default: String): String = environment(property) ?: default

public fun environmentInt(property: HotReloadProperty): Int? = environment(property)?.toInt()
